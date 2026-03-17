package com.ahogek.cttserver.common.idempotent.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lightweight Redis-based idempotent lock controller.
 *
 * <p>Uses Redis SETNX (SET IF NOT EXISTS) command to implement distributed locking with O(1) time
 * complexity. Provides try-lock and unlock operations for idempotency control.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Component
public class IdempotentLocker {

    private final StringRedisTemplate redisTemplate;

    public IdempotentLocker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempts to acquire a distributed lock.
     *
     * <p>Uses Redis SETNX command with expiration to prevent deadlocks.
     *
     * @param lockKey the Redis key for the lock
     * @param expireSeconds lock expiration time in seconds
     * @return true if lock acquired successfully (request allowed), false if lock is held
     *     (duplicate request blocked)
     */
    public boolean tryLock(String lockKey, long expireSeconds) {
        Boolean success =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(expireSeconds));
        return Boolean.TRUE.equals(success);
    }

    /**
     * Releases the distributed lock.
     *
     * <p>Called proactively when business execution fails, allowing immediate retry with corrected
     * parameters.
     *
     * @param lockKey the Redis key for the lock
     */
    public void unlock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
