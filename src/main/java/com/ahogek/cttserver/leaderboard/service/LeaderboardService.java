package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.leaderboard.dto.LeaderboardEntryDto;
import com.ahogek.cttserver.leaderboard.dto.LeaderboardResponse;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.stats.service.StatsCalculator;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
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
 * streak change at all. Recomputing one user's score keeps the ranking immediately correct with a
 * single-user cost; other members' scores are untouched. The leaderboard is derived state, so a
 * Redis failure is logged and swallowed — the next push recomputes the score and self-heals.
 *
 * <p>Ranking semantics: {@link LeaderboardDimension#TOTAL} is the merged lifetime coding duration
 * (same semantics as the stats summary total) and {@link LeaderboardDimension#STREAK} is the
 * longest consecutive coding-day streak. Both use UTC so the global ranking has a single,
 * server-deterministic timezone.
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

    public LeaderboardService(
            StringRedisTemplate redisTemplate,
            CodingSessionRepository codingSessionRepository,
            UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.codingSessionRepository = codingSessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Recomputes the user's score for a dimension and writes it into the ZSet.
     *
     * <p>The database read joins the surrounding transaction, so a push-triggered call observes the
     * sessions just written by the same push. Failures are logged and swallowed — the leaderboard
     * is derived state and the next push recomputes the score.
     *
     * @param userId the owning user
     * @param dimension the ranking dimension
     */
    @Transactional(readOnly = true)
    public void updateUserScore(UUID userId, LeaderboardDimension dimension) {
        // Serialize the read-compute-write per user: under concurrent same-user pushes a plain
        // recompute could read a pre-commit snapshot and overwrite the other push's sessions in
        // the ZSet. The short-lived per-user lock makes that interleaving impossible; on contention
        // we retry briefly, then fall through with a best-effort recompute (the next push heals any
        // residual staleness anyway).
        String lockKey = LOCK_PREFIX + userId;
        try {
            if (tryAcquireLock(lockKey)) {
                try {
                    recomputeAndWrite(userId, dimension);
                } finally {
                    redisTemplate.delete(lockKey);
                }
            } else {
                log.warn(
                        "Leaderboard lock contention for user {} dimension {}, recomputing anyway",
                        userId,
                        dimension);
                recomputeAndWrite(userId, dimension);
            }
        } catch (Exception e) {
            log.error(
                    "Failed to update leaderboard score for user {} dimension {}",
                    userId,
                    dimension,
                    e);
        }
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

    private void recomputeAndWrite(UUID userId, LeaderboardDimension dimension) {
        List<CodingSession> sessions =
                codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId);
        long score = computeScore(sessions, dimension);
        redisTemplate.opsForZSet().add(key(dimension), userId.toString(), score);
    }

    /**
     * Returns one page of the leaderboard with the calling user's rank.
     *
     * @param dimension the ranking dimension
     * @param limit page size
     * @param offset zero-based start index
     * @param currentUserId the calling user
     * @return the ranked page and the caller's rank (or {@code null} when not ranked)
     */
    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(
            LeaderboardDimension dimension, int limit, int offset, UUID currentUserId) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate
                        .opsForZSet()
                        .reverseRangeWithScores(key(dimension), offset, (long) offset + limit - 1);

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

        Long rawRank =
                redisTemplate.opsForZSet().reverseRank(key(dimension), currentUserId.toString());
        Long currentUserRank = rawRank != null ? rawRank + 1 : null;
        return new LeaderboardResponse(entries, currentUserRank);
    }

    private long computeScore(List<CodingSession> sessions, LeaderboardDimension dimension) {
        ZoneOffset utc = ZoneOffset.UTC;
        LocalDate today = LocalDate.now(utc);
        return switch (dimension) {
            case TOTAL -> StatsCalculator.summary(sessions, utc, today).total();
            case STREAK -> StatsCalculator.streaks(sessions, utc, today).max();
        };
    }

    private String key(LeaderboardDimension dimension) {
        return KEY_PREFIX + dimension.name().toLowerCase();
    }
}
