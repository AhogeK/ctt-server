package com.ahogek.cttserver.common.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-based per-key mutex with bounded retry.
 *
 * <p>Acquisition uses {@code SET key value NX EX ttl}: the first caller wins and later callers poll
 * briefly before giving up, so contention degrades to "lock not acquired" instead of blocking.
 * Callers own the release — always {@link #release} in a {@code finally} block.
 *
 * <p>Consumers: the leaderboard score recompute and the daily-stats materializer, which serialize
 * per-user read-compute-write cycles against concurrent pushes.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Service
public class RedisLockService {

    /** Fixed retry budget shared by all consumers: 5 attempts, 50ms apart. */
    private static final int LOCK_ATTEMPTS = 5;

    private static final long LOCK_RETRY_MILLIS = 50;

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Tries to acquire the lock for the given key, retrying briefly while it is held.
     *
     * @param lockKey the key to lock on
     * @param ttl how long the lock survives without an explicit release (crash safety)
     * @return {@code true} when the lock was acquired, {@code false} when it stayed held through
     *     every retry or the wait was interrupted
     */
    public boolean tryAcquire(String lockKey, Duration ttl) {
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", ttl))) {
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

    /**
     * Releases a previously acquired lock.
     *
     * @param lockKey the key to unlock
     */
    public void release(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
