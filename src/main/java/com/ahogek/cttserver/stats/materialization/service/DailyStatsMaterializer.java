package com.ahogek.cttserver.stats.materialization.service;

import com.ahogek.cttserver.common.lock.RedisLockService;
import com.ahogek.cttserver.stats.materialization.repository.DailyStatsRepository;
import com.ahogek.cttserver.stats.service.StatsCalculator;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maintains the per-user per-UTC-day materialized statistics.
 *
 * <p>Two write paths keep {@code daily_stats} in sync with {@code coding_sessions}:
 *
 * <ul>
 *   <li><strong>Incremental</strong> — after a push, only the UTC dates the pushed sessions touch
 *       are recomputed from the sessions overlapping those dates and upserted. Cost is bounded by
 *       the touched dates, independent of history size.
 *   <li><strong>Lazy bootstrap</strong> — a user whose history is not yet materialized (no
 *       bootstrapped marker row) is fully rebuilt once on the first statistics read, under the
 *       per-user lock the leaderboard uses.
 * </ul>
 *
 * <p>The table is derived state: recomputes always read the authoritative sessions, so drift from
 * soft deletes or LWW updates self-corrects on the next touched-day recompute, and a Redis failure
 * never propagates (the next push retries).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Service
public class DailyStatsMaterializer {

    private static final Logger log = LoggerFactory.getLogger(DailyStatsMaterializer.class);

    private static final String LOCK_PREFIX = "daily_stats:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private final CodingSessionRepository codingSessionRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisLockService redisLock;

    public DailyStatsMaterializer(
            CodingSessionRepository codingSessionRepository,
            DailyStatsRepository dailyStatsRepository,
            StringRedisTemplate redisTemplate,
            RedisLockService redisLock) {
        this.codingSessionRepository = codingSessionRepository;
        this.dailyStatsRepository = dailyStatsRepository;
        this.redisTemplate = redisTemplate;
        this.redisLock = redisLock;
    }

    /**
     * Recomputes the materialized rows for the UTC dates the given sessions touch.
     *
     * @param userId the owning user
     * @param touchedDates the UTC dates to recompute (derived from the pushed sessions)
     */
    @Transactional
    public void recomputeDays(UUID userId, List<LocalDate> touchedDates) {
        if (touchedDates.isEmpty()) {
            return;
        }
        String lockKey = LOCK_PREFIX + userId;
        try {
            if (redisLock.tryAcquire(lockKey, LOCK_TTL)) {
                try {
                    recompute(userId, touchedDates, false);
                } finally {
                    redisLock.release(lockKey);
                }
            } else {
                log.warn("Daily-stats lock contention for user {}, recomputing anyway", userId);
                recompute(userId, touchedDates, false);
            }
        } catch (Exception e) {
            log.error("Failed to recompute daily stats for user {}", userId, e);
        }
    }

    /**
     * Bootstraps the user's full materialized history once, when the bootstrapped marker is absent.
     * Returns {@code true} when rows are available afterwards.
     *
     * @param userId the owning user
     * @return {@code true} when materialized rows cover the user's history
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean bootstrapIfNeeded(UUID userId) {
        if (dailyStatsRepository.existsBootstrapped(userId)) {
            return true;
        }
        String lockKey = LOCK_PREFIX + userId;
        try {
            if (redisLock.tryAcquire(lockKey, LOCK_TTL)) {
                try {
                    if (dailyStatsRepository.existsBootstrapped(userId)) {
                        return true;
                    }
                    rebuildAll(userId);
                    return true;
                } finally {
                    redisLock.release(lockKey);
                }
            }
            log.info("Daily-stats bootstrap already running for user {}", userId);
            return false;
        } catch (Exception e) {
            log.error("Failed to bootstrap daily stats for user {}", userId, e);
            return false;
        }
    }

    private void recompute(UUID userId, List<LocalDate> days, boolean bootstrapped) {
        LocalDate min = days.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate max = days.stream().max(LocalDate::compareTo).orElseThrow();
        List<CodingSession> sessions =
                codingSessionRepository.findLiveInUtcDayRange(
                        userId,
                        min.atStartOfDay().toInstant(ZoneOffset.UTC),
                        max.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        Map<LocalDate, Long> secondsByDay =
                StatsCalculator.mergedSecondsByDay(sessions, ZoneOffset.UTC);
        for (LocalDate day : days) {
            dailyStatsRepository.upsertDay(
                    userId, day, secondsByDay.getOrDefault(day, 0L), bootstrapped);
        }
    }

    private void rebuildAll(UUID userId) {
        List<CodingSession> sessions =
                codingSessionRepository.findAllByUserIdAndIsDeletedFalse(userId);
        dailyStatsRepository.deleteByUserId(userId);
        Map<LocalDate, Long> secondsByDay =
                StatsCalculator.mergedSecondsByDay(sessions, ZoneOffset.UTC);
        for (Map.Entry<LocalDate, Long> entry : secondsByDay.entrySet()) {
            dailyStatsRepository.upsertDay(userId, entry.getKey(), entry.getValue(), true);
        }
        log.info("Bootstrapped daily stats for user {} ({} days)", userId, secondsByDay.size());
    }
}
