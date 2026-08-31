package com.ahogek.cttserver.stats.achievement.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.stats.achievement.dto.AchievementResponse;
import com.ahogek.cttserver.stats.achievement.entity.UserAchievement;
import com.ahogek.cttserver.stats.achievement.enums.Achievement;
import com.ahogek.cttserver.stats.achievement.enums.AchievementType;
import com.ahogek.cttserver.stats.achievement.repository.UserAchievementRepository;
import com.ahogek.cttserver.stats.service.StatsCalculator;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Achievement service that lazily evaluates badges against the user's coding sessions.
 *
 * <p>Evaluation happens on query (no background job): every achievement's progress is derived from
 * the same session set, and any badge whose progress reaches its target is unlocked. Unlocks are
 * idempotent via an {@code INSERT ... ON CONFLICT DO NOTHING} write, so concurrent requests cannot
 * double-award a badge, and each newly inserted unlock emits one {@code ACHIEVEMENT_UNLOCKED} audit
 * event. Time-window badges (early bird / night owl / perfect month) use the caller-provided zone
 * like the rest of the personal statistics endpoints.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Service
public class AchievementService {

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    private static final int EARLY_BIRD_START_HOUR = 6;
    private static final int EARLY_BIRD_END_HOUR = 9;
    private static final int NIGHT_OWL_START_HOUR = 22;
    private static final int NIGHT_OWL_END_HOUR = 5;

    private static final String CACHE_PREFIX = "achievements:cache:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final CodingSessionRepository codingSessionRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AchievementService(
            CodingSessionRepository codingSessionRepository,
            UserAchievementRepository userAchievementRepository,
            AuditLogService auditLogService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this(
                codingSessionRepository,
                userAchievementRepository,
                auditLogService,
                Clock.systemUTC(),
                redisTemplate,
                objectMapper);
    }

    AchievementService(
            CodingSessionRepository codingSessionRepository,
            UserAchievementRepository userAchievementRepository,
            AuditLogService auditLogService,
            Clock clock,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.codingSessionRepository = codingSessionRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns every achievement with its unlock state and progress, unlocking any newly reached
     * badge along the way.
     *
     * @param userId the owning user
     * @param zone the aggregation timezone for window-based badges
     * @return all achievements, in declaration order
     */
    @Transactional
    public List<AchievementResponse> getAchievements(UUID userId, ZoneOffset zone) {
        String cacheKey = CACHE_PREFIX + userId + ":" + zone.getTotalSeconds();
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(
                        cached,
                        objectMapper
                                .getTypeFactory()
                                .constructCollectionType(List.class, AchievementResponse.class));
            }
        } catch (Exception e) {
            log.warn("Achievements cache read failed for user {}, falling back", userId, e);
        }
        List<AchievementResponse> result = evaluate(userId, zone);
        try {
            redisTemplate
                    .opsForValue()
                    .set(cacheKey, objectMapper.writeValueAsString(result), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Achievements cache write failed for user {}", userId, e);
        }
        return result;
    }

    /** Invalidates cached achievement lists after a push (new sessions change progress). */
    public void evictCache(UUID userId) {
        try {
            // SCAN is used instead of KEYS to avoid blocking the keyspace; the per-user cache
            // holds at most one entry per timezone so this is bounded in practice.
            Set<String> keys = new HashSet<>();
            var scanOptions =
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(CACHE_PREFIX + userId + ":*")
                            .count(100)
                            .build();
            try (var cursor = redisTemplate.scan(scanOptions)) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // failure-tolerant: a stale cache entry expires by TTL, the push must not roll back
            log.warn("Achievements cache eviction failed for user {}", userId, e);
        }
    }

    private List<AchievementResponse> evaluate(UUID userId, ZoneOffset zone) {
        List<CodingSession> sessions =
                codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId);
        Map<String, Instant> unlockedAt = new HashMap<>();
        for (UserAchievement unlock : userAchievementRepository.findByUserId(userId)) {
            unlockedAt.put(unlock.getAchievementCode(), unlock.getUnlockedAt());
        }

        List<AchievementResponse> result = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            long progress = progress(achievement.type(), sessions, zone);
            boolean unlocked = unlockedAt.containsKey(achievement.name());
            Instant timestamp = unlockedAt.get(achievement.name());
            if (!unlocked && progress >= achievement.target()) {
                int inserted = userAchievementRepository.insertIfAbsent(userId, achievement.name());
                if (inserted == 1) {
                    // unlocked_at is set by the database (DEFAULT CURRENT_TIMESTAMP); re-read it so
                    // this and later queries return the same value — the DB clock is authoritative
                    // and the service clock may drift from it.
                    timestamp =
                            userAchievementRepository.findByUserId(userId).stream()
                                    .filter(
                                            unlock ->
                                                    unlock.getAchievementCode()
                                                            .equals(achievement.name()))
                                    .map(UserAchievement::getUnlockedAt)
                                    .findFirst()
                                    .orElse(null);
                    auditLogService.logSuccess(
                            userId,
                            AuditAction.ACHIEVEMENT_UNLOCKED,
                            ResourceType.ACHIEVEMENT,
                            achievement.name());
                    log.info("Achievement {} unlocked for user {}", achievement.name(), userId);
                }
                unlocked = true;
            }
            result.add(
                    new AchievementResponse(
                            achievement.name(),
                            achievement.displayName(),
                            achievement.description(),
                            unlocked,
                            timestamp,
                            progress,
                            achievement.target(),
                            achievement.unit()));
        }
        return result;
    }

    private long progress(AchievementType type, List<CodingSession> sessions, ZoneOffset zone) {
        return switch (type) {
            case STREAK -> StatsCalculator.streaks(sessions, zone, LocalDate.now(clock)).max();
            case TOTAL_SECONDS ->
                    StatsCalculator.summary(sessions, zone, LocalDate.now(clock)).total();
            case LANGUAGE_COUNT ->
                    StatsCalculator.accumulateBy(sessions, zone, CodingSession::getLanguage).size();
            case EARLY_BIRD_DAYS ->
                    StatsCalculator.activeDaysInDailyWindow(
                            sessions, zone, EARLY_BIRD_START_HOUR, EARLY_BIRD_END_HOUR);
            case NIGHT_OWL_DAYS ->
                    StatsCalculator.activeDaysInDailyWindow(
                            sessions, zone, NIGHT_OWL_START_HOUR, NIGHT_OWL_END_HOUR);
            case MAX_DAILY_SECONDS -> StatsCalculator.maxDailySeconds(sessions, zone);
            case PERFECT_MONTH -> StatsCalculator.hasPerfectMonth(sessions, zone) ? 1 : 0;
        };
    }
}
