package com.ahogek.cttserver.stats.achievement.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.stats.achievement.dto.AchievementResponse;
import com.ahogek.cttserver.stats.achievement.entity.AchievementProgress;
import com.ahogek.cttserver.stats.achievement.entity.UserAchievement;
import com.ahogek.cttserver.stats.achievement.enums.Achievement;
import com.ahogek.cttserver.stats.achievement.enums.Achievement.LadderKey;
import com.ahogek.cttserver.stats.achievement.enums.AchievementWindow;
import com.ahogek.cttserver.stats.achievement.repository.AchievementProgressRepository;
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
import java.util.LinkedHashMap;
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
    private static final String CACHE_PREFIX = "achievements:cache:v3:";

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final CodingSessionRepository codingSessionRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementProgressRepository achievementProgressRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AchievementService(
            CodingSessionRepository codingSessionRepository,
            UserAchievementRepository userAchievementRepository,
            AchievementProgressRepository achievementProgressRepository,
            AuditLogService auditLogService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this(
                codingSessionRepository,
                userAchievementRepository,
                achievementProgressRepository,
                auditLogService,
                Clock.systemUTC(),
                redisTemplate,
                objectMapper);
    }

    AchievementService(
            CodingSessionRepository codingSessionRepository,
            UserAchievementRepository userAchievementRepository,
            AchievementProgressRepository achievementProgressRepository,
            AuditLogService auditLogService,
            Clock clock,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.codingSessionRepository = codingSessionRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.achievementProgressRepository = achievementProgressRepository;
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
        LocalDate today = LocalDate.now(clock.withZone(zone));

        // Unlocks are matched by code AND period: a lifetime badge has one row, a windowed badge
        // has
        // one per period it was earned in, and only the current period's row counts as unlocked.
        // Keyed by code, not by ladder, so each rung reports the instant it was itself earned.
        Map<String, Instant> unlockedByCode = new HashMap<>();
        for (UserAchievement unlock : userAchievementRepository.findByUserId(userId)) {
            Achievement achievement = Achievement.byCode(unlock.getAchievementCode());
            if (achievement == null
                    || !achievement.window().periodKey(today).equals(unlock.getPeriodKey())) {
                continue;
            }
            unlockedByCode.put(unlock.getAchievementCode(), unlock.getUnlockedAt());
        }

        Map<LadderKey, Long> highWater = new LinkedHashMap<>();
        for (AchievementProgress mark : achievementProgressRepository.findByUserId(userId)) {
            // Only lifetime ladders are ever stored, so a row names the family's perpetual ladder.
            highWater.put(
                    new LadderKey(mark.getAchievementType(), AchievementWindow.LIFETIME),
                    mark.getProgress());
        }

        Map<LadderKey, Measurement> measurements = new LinkedHashMap<>();
        Map<LadderKey, Long> reportedProgress = new LinkedHashMap<>();
        for (LadderKey ladder : LadderKey.allInDeclarationOrder()) {
            Measurement measurement = measure(ladder, sessions, zone, today);
            measurements.put(ladder, measurement);
            long progress = Math.max(measurement.progress(), floorOf(ladder, unlockedByCode));
            if (ladder.window() == AchievementWindow.LIFETIME) {
                // Lifetime progress accumulates over all history, so it must not fall back when
                // the sessions behind it are deleted. Windowed progress is expected to reset each
                // period, so a mark would defeat the point.
                long previous = highWater.getOrDefault(ladder, 0L);
                if (progress > previous) {
                    achievementProgressRepository.raiseIfHigher(
                            userId, ladder.type().name(), progress);
                } else {
                    progress = previous;
                }
            }
            reportedProgress.put(ladder, progress);
        }

        List<AchievementResponse> result = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            LadderKey ladder = new LadderKey(achievement.type(), achievement.window());
            long progress = reportedProgress.get(ladder);
            Measurement measurement = measurements.get(ladder);
            boolean unlocked = unlockedByCode.containsKey(achievement.name());
            Instant timestamp = unlockedByCode.get(achievement.name());
            if (!unlocked && progress >= achievement.target()) {
                Instant achievedAt = measurement.achievedAt(achievement.target());
                if (achievedAt == null) {
                    // progress and the milestone instant come from the same computation, so this
                    // only trips when a raised high-water mark outlives the sessions behind it;
                    // record the unlock rather than lose it, and leave a trail instead of silently
                    // writing a wrong instant.
                    log.warn(
                            "Achievement {} reached its target without a resolvable instant for user {};"
                                    + " recording the observation time",
                            achievement.name(),
                            userId);
                    achievedAt = Instant.now(clock);
                }
                String periodKey = achievement.window().periodKey(today);
                int inserted =
                        userAchievementRepository.insertIfAbsent(
                                userId,
                                achievement.name(),
                                periodKey,
                                achievedAt.atOffset(ZoneOffset.UTC));
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
                    timestamp = reloadedUnlockedAt(userId, achievement.name(), periodKey);
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
                            achievement.unit(),
                            achievement.window().name(),
                            achievement.window().start(today),
                            achievement.window().end(today)));
        }
        return result;
    }

    /**
     * Returns the lowest value a ladder's progress can honestly report, given the badges already
     * awarded for it in the current period.
     *
     * <p>A badge that was unlocked proves its target was once reached, so the highest awarded
     * target in the ladder is a floor the current measurement must not undercut. This also
     * back-fills badges unlocked before high-water marks existed, which have no stored mark.
     *
     * @param ladder the ladder
     * @param unlockedByCode the badges the user has unlocked in the current period, by code
     * @return the highest target already reached, or 0 when the ladder has no unlocked badge
     */
    private static long floorOf(LadderKey ladder, Map<String, Instant> unlockedByCode) {
        long floor = 0;
        for (Achievement achievement : Achievement.values()) {
            if (achievement.type() == ladder.type()
                    && achievement.window() == ladder.window()
                    && unlockedByCode.containsKey(achievement.name())) {
                floor = Math.max(floor, achievement.target());
            }
        }
        return floor;
    }

    private Instant reloadedUnlockedAt(UUID userId, String achievementCode, String periodKey) {
        return userAchievementRepository.findByUserId(userId).stream()
                .filter(
                        unlock ->
                                unlock.getAchievementCode().equals(achievementCode)
                                        && unlock.getPeriodKey().equals(periodKey))
                .map(UserAchievement::getUnlockedAt)
                .findFirst()
                .orElse(null);
    }

    /**
     * A ladder's current progress together with a resolver for the instant a given target was met.
     *
     * <p>Both come from the same computation, so the reported instant always belongs to the data
     * that produced the progress; the resolver is keyed by target because one ladder carries
     * several rungs and each was earned at a different moment.
     *
     * @param progress the ladder's current measured value
     * @param resolver maps a rung's target to the instant it was reached, or {@code null} when it
     *     was not
     */
    private record Measurement(long progress, LongFunction<Instant> resolver) {

        Instant achievedAt(long target) {
            return resolver.apply(target);
        }
    }

    /**
     * Measures one ladder over its own window.
     *
     * <p>Sessions are clipped to the ladder's period first, so a windowed badge counts only the
     * coding done inside it: today's 2 hours, this week's 5 active days. Clip boundaries follow the
     * caller's local calendar, matching every statistics endpoint.
     */
    private Measurement measure(
            LadderKey ladder, List<CodingSession> sessions, ZoneOffset zone, LocalDate today) {
        List<CodingSession> windowed =
                StatsCalculator.clipSessions(
                        sessions, zone, ladder.window().start(today), ladder.window().end(today));
        return switch (ladder.type()) {
            case STREAK -> {
                long progress = StatsCalculator.streaks(windowed, zone, today).max();
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.streakAchievedAt(
                                        windowed, zone, Math.toIntExact(target)));
            }
            case TOTAL_SECONDS -> {
                long progress = StatsCalculator.summary(windowed, zone, today).total();
                yield new Measurement(
                        progress,
                        target -> StatsCalculator.totalSecondsAchievedAt(windowed, zone, target));
            }
            case ACTIVE_DAYS -> {
                long progress = StatsCalculator.activeDayCount(windowed, zone);
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.activeDaysAchievedAt(
                                        windowed, zone, Math.toIntExact(target)));
            }
            case LANGUAGE_COUNT -> {
                long progress =
                        StatsCalculator.accumulateBy(windowed, zone, CodingSession::getLanguage)
                                .size();
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.languageCountAchievedAt(
                                        windowed, zone, Math.toIntExact(target)));
            }
            case EARLY_BIRD_DAYS ->
                    windowDaysMeasurement(
                            windowed, zone, EARLY_BIRD_START_HOUR, EARLY_BIRD_END_HOUR);
            case NIGHT_OWL_DAYS ->
                    windowDaysMeasurement(windowed, zone, NIGHT_OWL_START_HOUR, NIGHT_OWL_END_HOUR);
            case MAX_DAILY_SECONDS -> {
                long progress = StatsCalculator.maxDailySeconds(windowed, zone);
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.maxDailySecondsAchievedAt(windowed, zone, target));
            }
            case PERFECT_MONTH -> {
                long progress = StatsCalculator.bestPerfectMonthPercent(windowed, zone);
                yield new Measurement(
                        progress,
                        target ->
                                StatsCalculator.perfectMonthPercentAchievedAt(
                                        windowed, zone, target));
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
