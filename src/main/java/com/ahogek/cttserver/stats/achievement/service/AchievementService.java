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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;

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

    /**
     * Cache key prefix, carrying a format version.
     *
     * <p>The cached value is the serialized {@link AchievementResponse} list, so a release that
     * changes the response shape must not read entries written by the previous build: Jackson fills
     * absent components with defaults rather than failing ({@code type} would come back {@code
     * null}, {@code tier} {@code 0}) and the stale ladder would be served for the rest of the
     * entry's TTL. Bump the version whenever the shape changes — old keys then simply expire.
     */
    private static final String CACHE_PREFIX = "achievements:cache:v2:";

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

        // One measurement per type, not per badge: a family with eight rungs would otherwise
        // re-scan the whole session history eight times for the same number.
        Map<AchievementType, Measurement> measurements = new EnumMap<>(AchievementType.class);

        List<AchievementResponse> result = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            Measurement measurement =
                    measurements.computeIfAbsent(
                            achievement.type(), type -> measure(type, sessions, zone));
            long progress = measurement.progress();
            boolean unlocked = unlockedAt.containsKey(achievement.name());
            Instant timestamp = unlockedAt.get(achievement.name());
            if (!unlocked && progress >= achievement.target()) {
                Instant achievedAt = measurement.achievedAt(achievement.target());
                if (achievedAt == null) {
                    // progress and the milestone instant come from the same computation, so this
                    // only trips if the two ever disagree; record the unlock rather than lose it,
                    // and leave a trail instead of silently writing a wrong instant.
                    log.warn(
                            "Achievement {} reached its target without a resolvable instant for user {};"
                                    + " recording the observation time",
                            achievement.name(),
                            userId);
                    achievedAt = Instant.now(clock);
                }
                int inserted =
                        userAchievementRepository.insertIfAbsent(
                                userId, achievement.name(), achievedAt.atOffset(ZoneOffset.UTC));
                if (inserted == 1) {
                    timestamp = achievedAt;
                    auditLogService.logSuccess(
                            userId,
                            AuditAction.ACHIEVEMENT_UNLOCKED,
                            ResourceType.ACHIEVEMENT,
                            achievement.name());
                    log.info("Achievement {} unlocked for user {}", achievement.name(), userId);
                } else {
                    // a concurrent request won the insert; its instant is the recorded one
                    timestamp = reloadedUnlockedAt(userId, achievement.name());
                }
                unlocked = true;
            }
            result.add(
                    new AchievementResponse(
                            achievement.name(),
                            achievement.type().name(),
                            achievement.tier(),
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

    private Instant reloadedUnlockedAt(UUID userId, String achievementCode) {
        return userAchievementRepository.findByUserId(userId).stream()
                .filter(unlock -> unlock.getAchievementCode().equals(achievementCode))
                .map(UserAchievement::getUnlockedAt)
                .findFirst()
                .orElse(null);
    }

    /**
     * A family's current progress together with a resolver for the instant a given target was met.
     *
     * <p>Both come from the same computation, so the reported instant always belongs to the data
     * that produced the progress; the resolver is keyed by target because one family carries
     * several rungs and each was earned at a different moment.
     *
     * @param progress the family's current measured value
     * @param resolver maps a rung's target to the instant it was reached, or {@code null} when it
     *     was not
     */
    private record Measurement(long progress, LongFunction<Instant> resolver) {

        Instant achievedAt(long target) {
            return resolver.apply(target);
        }
    }

    private Measurement measure(
            AchievementType type, List<CodingSession> sessions, ZoneOffset zone) {
        // The clock is projected into the caller's zone, not left in UTC: a "today" derived from
        // UTC would make the streak and summary windows disagree with every statistics endpoint,
        // which resolves today in the request's own zone.
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return switch (type) {
            case STREAK -> {
                long progress = StatsCalculator.streaks(sessions, zone, today).max();
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.streakAchievedAt(
                                        sessions, zone, Math.toIntExact(target)));
            }
            case TOTAL_SECONDS -> {
                long progress = StatsCalculator.summary(sessions, zone, today).total();
                yield new Measurement(
                        progress,
                        target -> StatsCalculator.totalSecondsAchievedAt(sessions, zone, target));
            }
            case LANGUAGE_COUNT -> {
                long progress =
                        StatsCalculator.accumulateBy(sessions, zone, CodingSession::getLanguage)
                                .size();
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.languageCountAchievedAt(
                                        sessions, zone, Math.toIntExact(target)));
            }
            case EARLY_BIRD_DAYS ->
                    windowDaysMeasurement(
                            sessions, zone, EARLY_BIRD_START_HOUR, EARLY_BIRD_END_HOUR);
            case NIGHT_OWL_DAYS ->
                    windowDaysMeasurement(sessions, zone, NIGHT_OWL_START_HOUR, NIGHT_OWL_END_HOUR);
            case MAX_DAILY_SECONDS -> {
                long progress = StatsCalculator.maxDailySeconds(sessions, zone);
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.maxDailySecondsAchievedAt(sessions, zone, target));
            }
            case PERFECT_MONTH -> {
                long progress = StatsCalculator.bestPerfectMonthPercent(sessions, zone);
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.perfectMonthPercentAchievedAt(
                                        sessions, zone, target));
            }
        };
    }

    private Measurement windowDaysMeasurement(
            List<CodingSession> sessions, ZoneOffset zone, int startHour, int endHour) {
        long progress = StatsCalculator.activeDaysInDailyWindow(sessions, zone, startHour, endHour);
        return new Measurement(
                progress,
                target ->
                        StatsCalculator.activeWindowDaysAchievedAt(
                                sessions, zone, startHour, endHour, Math.toIntExact(target)));
    }
}
