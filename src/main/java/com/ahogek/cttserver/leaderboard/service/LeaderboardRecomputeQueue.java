package com.ahogek.cttserver.leaderboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Coalesces leaderboard recomputes off the push write path.
 *
 * <p>A push used to recompute the pushing user's scores inside its own transaction, which made push
 * latency grow with the user's whole session history (the recompute loads every session they ever
 * wrote and rewrites seven dimensions across six periods plus their language boards). This
 * component replaces that with a dirty mark: the push pays one Redis {@code SADD}, and a scheduled
 * drain does the actual recompute for whoever is marked.
 *
 * <p>The mark is a set, so N pushes for one user inside one drain interval cost one recompute —
 * coalescing falls out of the data structure rather than needing a per-user pending flag.
 *
 * <p>Ordering matters: the mark is registered as an {@code afterCommit} synchronization when a
 * transaction is active. Marking before commit would let a drain read the pre-commit snapshot,
 * write scores that are already stale, and clear the mark — leaving nothing to heal it until that
 * user's next push. Rollbacks therefore never queue work, because there is no work to do.
 *
 * <p>Draining pops members atomically, so two instances never recompute the same user, and a
 * concurrent push marking a user mid-drain survives the pop and is picked up by the next turn.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-05
 */
@Component
public class LeaderboardRecomputeQueue {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardRecomputeQueue.class);

    /** User ids whose scores are known to be stale. A set, so repeated marks coalesce. */
    static final String DIRTY_KEY = "leaderboard:dirty";

    /** Bounds one drain turn so a backlog cannot pin the scheduler thread. */
    private static final long DRAIN_BATCH = 100;

    private final StringRedisTemplate redisTemplate;
    private final LeaderboardService leaderboardService;

    public LeaderboardRecomputeQueue(
            StringRedisTemplate redisTemplate, LeaderboardService leaderboardService) {
        this.redisTemplate = redisTemplate;
        this.leaderboardService = leaderboardService;
    }

    /**
     * Records that this user's scores no longer match their sessions.
     *
     * <p>Safe to call repeatedly and from inside a transaction: the write is deferred to commit and
     * the set absorbs duplicates. Outside a transaction (tests, scheduled code) it writes
     * immediately.
     *
     * @param userId the user whose scores are now stale
     */
    public void markDirty(UUID userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            queue(userId);
                        }
                    });
            return;
        }
        queue(userId);
    }

    /**
     * Recomputes every user marked dirty since the previous turn.
     *
     * <p>{@link LeaderboardService#updateUserScores} swallows its own failures, so a user it could
     * not recompute is dropped from the set and healed by that user's next push — the same recovery
     * the recompute path already relied on. A Redis failure while popping propagates here and is
     * logged by the scheduler; the marks stay in the set and the next turn retries them.
     */
    @Scheduled(fixedDelayString = "${ctt.leaderboard.recompute-drain-interval-ms:5000}")
    public void drainDirty() {
        List<String> stale = redisTemplate.opsForSet().pop(DIRTY_KEY, DRAIN_BATCH);
        if (stale == null || stale.isEmpty()) {
            return;
        }

        int recomputed = 0;
        for (String raw : stale) {
            UUID userId;
            try {
                userId = UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                log.warn("Dropping malformed leaderboard dirty mark '{}'", raw);
                continue;
            }
            leaderboardService.updateUserScores(userId);
            recomputed++;
        }
        log.atInfo().log(
                "Recomputed leaderboard scores for {} of {} dirty users", recomputed, stale.size());
    }

    /**
     * Reports how many users are waiting for a recompute.
     *
     * <p>Reads the size of the dirty set, which the drain pops in batches. It is a backlog depth,
     * not a cumulative count: a mark disappears as soon as a drain turn picks it up.
     *
     * @return the number of users currently marked dirty
     */
    public long pendingCount() {
        Long size = redisTemplate.opsForSet().size(DIRTY_KEY);
        return size == null ? 0L : size;
    }

    private void queue(UUID userId) {
        try {
            redisTemplate.opsForSet().add(DIRTY_KEY, userId.toString());
        } catch (RuntimeException e) {
            // Scores stay stale until this user pushes again, which is the recovery the
            // recompute path already depends on. Never let a Redis hiccup fail the commit.
            log.warn("Could not queue user {} for leaderboard recompute", userId, e);
        }
    }
}
