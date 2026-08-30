package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.leaderboard.dto.LeaderboardEntryDto;
import com.ahogek.cttserver.leaderboard.dto.LeaderboardResponse;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardPeriod;
import com.ahogek.cttserver.stats.service.StatsCalculator;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Global leaderboard backed by Redis ZSets.
 *
 * <p>Scores are recomputed from the database for the affected user and written with {@code ZADD}
 * (never incrementally accumulated): a soft-deleted or conflict-updated session would otherwise
 * make a running total drift from the real value, while an incremental write still cannot reflect a
 * streak change at all. Recomputing one user's scores keeps the ranking immediately correct with a
 * single-user cost; other members' scores are untouched. The leaderboard is derived state, so a
 * Redis failure is logged and swallowed — the next push recomputes the scores and self-heals.
 *
 * <p>Ranking semantics: {@link LeaderboardDimension#TOTAL} is the merged coding duration (same
 * semantics as the stats summary total) over the requested {@link LeaderboardPeriod}, {@link
 * LeaderboardDimension#STREAK} is the longest consecutive coding-day streak, {@link
 * LeaderboardDimension#NIGHT_OWL} and {@link LeaderboardDimension#EARLY_BIRD} are merged durations
 * inside the 22:00-05:00 and 06:00-09:00 daily windows, and {@link LeaderboardDimension#GROWTH} is
 * the week-over-week net growth in seconds. All boundaries use UTC so the global ranking has a
 * single, server-deterministic timezone. Period keys are bucketed by their period start (ISO Monday
 * for weeks) and expire once the period closes ({@link LeaderboardPeriod#ALL} never expires).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private static final String KEY_PREFIX = "leaderboard:";
    private static final String LOCK_PREFIX = "leaderboard:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final int LOCK_ATTEMPTS = 5;
    private static final long LOCK_RETRY_MILLIS = 50;

    private final StringRedisTemplate redisTemplate;
    private final CodingSessionRepository codingSessionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public LeaderboardService(
            StringRedisTemplate redisTemplate,
            CodingSessionRepository codingSessionRepository,
            UserRepository userRepository) {
        this(redisTemplate, codingSessionRepository, userRepository, Clock.systemUTC());
    }

    LeaderboardService(
            StringRedisTemplate redisTemplate,
            CodingSessionRepository codingSessionRepository,
            UserRepository userRepository,
            Clock clock) {
        this.redisTemplate = redisTemplate;
        this.codingSessionRepository = codingSessionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Recomputes the user's score for every supported dimension/period combination and writes them
     * into their ZSet keys.
     *
     * <p>The database read joins the surrounding transaction, so a push-triggered call observes the
     * sessions just written by the same push. Failures are logged and swallowed — the leaderboard
     * is derived state and the next push recomputes the scores.
     *
     * @param userId the owning user
     */
    @Transactional(readOnly = true)
    public void updateUserScores(UUID userId) {
        // Serialize the read-compute-write per user: under concurrent same-user pushes a plain
        // recompute could read a pre-commit snapshot and overwrite the other push's sessions in
        // the ZSet. The short-lived per-user lock makes that interleaving impossible; on contention
        // we retry briefly, then fall through with a best-effort recompute (the next push heals any
        // residual staleness anyway).
        String lockKey = LOCK_PREFIX + userId;
        try {
            if (tryAcquireLock(lockKey)) {
                try {
                    recomputeAndWriteAll(userId);
                } finally {
                    redisTemplate.delete(lockKey);
                }
            } else {
                log.warn("Leaderboard lock contention for user {}, recomputing anyway", userId);
                recomputeAndWriteAll(userId);
            }
        } catch (Exception e) {
            log.error("Failed to update leaderboard scores for user {}", userId, e);
        }
    }

    /**
     * Returns one page of the leaderboard with the calling user's rank.
     *
     * @param dimension the ranking dimension
     * @param period the time window
     * @param limit page size
     * @param offset zero-based start index
     * @param currentUserId the calling user
     * @return the ranked page and the caller's rank (or {@code null} when not ranked)
     * @throws ValidationException when the dimension does not support the period
     */
    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(
            LeaderboardDimension dimension,
            LeaderboardPeriod period,
            int limit,
            int offset,
            UUID currentUserId) {
        if (!dimension.supports(period)) {
            throw new ValidationException(
                    ErrorCode.COMMON_003,
                    "Dimension " + dimension + " does not support period " + period);
        }
        LocalDate today = LocalDate.now(clock);
        String key = key(dimension, period, today);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate
                        .opsForZSet()
                        .reverseRangeWithScores(key, offset, (long) offset + limit - 1);

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        if (tuples != null && !tuples.isEmpty()) {
            // A ZSet member always carries a value and score, but the API annotates both as
            // nullable; skip malformed entries defensively.
            List<ZSetOperations.TypedTuple<String>> valid =
                    tuples.stream()
                            .filter(t -> t.getValue() != null && t.getScore() != null)
                            .toList();
            List<UUID> userIds =
                    valid.stream()
                            .map(t -> UUID.fromString(Objects.requireNonNull(t.getValue())))
                            .toList();
            Map<UUID, User> users =
                    userRepository.findAllById(userIds).stream()
                            .collect(Collectors.toMap(User::getId, Function.identity()));
            long rank = (long) offset + 1;
            double previousScore = Double.NaN;
            long skippedTies = 0;
            for (ZSetOperations.TypedTuple<String> tuple : valid) {
                double score = Objects.requireNonNull(tuple.getScore());
                if (score != previousScore) {
                    rank += skippedTies;
                    skippedTies = 0;
                    previousScore = score;
                }
                UUID userId = UUID.fromString(Objects.requireNonNull(tuple.getValue()));
                User user = users.get(userId);
                entries.add(
                        new LeaderboardEntryDto(
                                userId,
                                user != null ? user.getDisplayName() : null,
                                (long) score,
                                rank));
                skippedTies++;
            }
        }

        Long rawRank = redisTemplate.opsForZSet().reverseRank(key, currentUserId.toString());
        Long currentUserRank = rawRank != null ? rawRank + 1 : null;
        return new LeaderboardResponse(entries, currentUserRank);
    }

    private void recomputeAndWriteAll(UUID userId) {
        List<CodingSession> sessions =
                codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId);
        LocalDate today = LocalDate.now(clock);
        for (LeaderboardDimension dimension : LeaderboardDimension.values()) {
            for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
                if (!dimension.supports(period)) {
                    continue;
                }
                long score = computeScore(sessions, dimension, period, today);
                String key = key(dimension, period, today);
                redisTemplate.opsForZSet().add(key, userId.toString(), score);
                Duration ttl = ttlFor(period);
                if (ttl != null) {
                    redisTemplate.expire(key, ttl);
                }
            }
        }
    }

    private long computeScore(
            List<CodingSession> sessions,
            LeaderboardDimension dimension,
            LeaderboardPeriod period,
            LocalDate today) {
        ZoneOffset utc = ZoneOffset.UTC;
        return switch (dimension) {
            case TOTAL -> {
                List<StatsCalculator.TimeInterval> intervals =
                        StatsCalculator.toIntervals(sessions, utc);
                yield switch (period) {
                    case ALL -> StatsCalculator.summary(sessions, utc, today).total();
                    case WEEK ->
                            StatsCalculator.mergedDurationSeconds(
                                    intervals,
                                    weekStart(today).atStartOfDay().atOffset(utc),
                                    weekStart(today).plusWeeks(1).atStartOfDay().atOffset(utc));
                    case MONTH ->
                            StatsCalculator.mergedDurationSeconds(
                                    intervals,
                                    today.withDayOfMonth(1).atStartOfDay().atOffset(utc),
                                    today.withDayOfMonth(1)
                                            .plusMonths(1)
                                            .atStartOfDay()
                                            .atOffset(utc));
                    case YEAR ->
                            StatsCalculator.mergedDurationSeconds(
                                    intervals,
                                    today.withDayOfYear(1).atStartOfDay().atOffset(utc),
                                    today.withDayOfYear(1)
                                            .plusYears(1)
                                            .atStartOfDay()
                                            .atOffset(utc));
                };
            }
            case STREAK -> StatsCalculator.streaks(sessions, utc, today).max();
            case NIGHT_OWL, EARLY_BIRD ->
                    StatsCalculator.mergedDurationInDailyWindow(
                            sessions,
                            utc,
                            dimension.windowStartHour(),
                            dimension.windowEndHour(),
                            OffsetDateTime.MIN,
                            today.plusDays(1).atStartOfDay().atOffset(utc));
            case GROWTH -> {
                List<StatsCalculator.TimeInterval> intervals =
                        StatsCalculator.toIntervals(sessions, utc);
                OffsetDateTime weekStart = weekStart(today).atStartOfDay().atOffset(utc);
                long thisWeek =
                        StatsCalculator.mergedDurationSeconds(
                                intervals, weekStart, weekStart.plusWeeks(1));
                long lastWeek =
                        StatsCalculator.mergedDurationSeconds(
                                intervals, weekStart.minusWeeks(1), weekStart);
                yield thisWeek - lastWeek;
            }
        };
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private String key(LeaderboardDimension dimension, LeaderboardPeriod period, LocalDate today) {
        String base = KEY_PREFIX + dimension.name().toLowerCase();
        return switch (period) {
            case ALL -> base;
            case WEEK -> base + ":week:" + weekStart(today);
            case MONTH -> base + ":month:" + today.withDayOfMonth(1);
            case YEAR -> base + ":year:" + today.withDayOfYear(1);
        };
    }

    private static Duration ttlFor(LeaderboardPeriod period) {
        return switch (period) {
            case ALL -> null;
            case WEEK -> Duration.ofDays(8);
            case MONTH -> Duration.ofDays(32);
            case YEAR -> Duration.ofDays(370);
        };
    }

    private boolean tryAcquireLock(String lockKey) {
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            if (Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL))) {
                return true;
            }
            try {
                Thread.sleep(LOCK_RETRY_MILLIS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
