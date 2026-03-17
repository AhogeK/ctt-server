package com.ahogek.cttserver.common.ratelimit.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Redis-based rate limiter using Lua script for atomic operations.
 *
 * <p>Implements fixed window algorithm with atomic increment and expiration. The Lua script ensures
 * thread-safe operations in distributed environments, preventing race conditions.
 *
 * <p>Algorithm: If current count >= limit, return 0 (blocked); otherwise increment and set expiry
 * on first request, return 1 (allowed).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        // Lua script for atomic rate limiting
        // KEYS[1] = redis key
        // ARGV[1] = limit (max requests)
        // ARGV[2] = windowSeconds (TTL)
        // Returns: 1 if allowed, 0 if blocked
        String lua =
                """
                local current = redis.call('get', KEYS[1])
                if current and tonumber(current) >= tonumber(ARGV[1]) then
                    return 0
                end
                current = redis.call('incr', KEYS[1])
                if tonumber(current) == 1 then
                    redis.call('expire', KEYS[1], tonumber(ARGV[2]))
                end
                return 1
                """;

        this.rateLimitScript = new DefaultRedisScript<>(lua, Long.class);
    }

    /**
     * Checks if the request is allowed under the rate limit.
     *
     * @param key the Redis key for the rate limit counter
     * @param limit the maximum number of requests allowed
     * @param windowSeconds the time window in seconds
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        Long result =
                redisTemplate.execute(
                        rateLimitScript,
                        Collections.singletonList(key),
                        String.valueOf(limit),
                        String.valueOf(windowSeconds));

        return result != null && result == 1L;
    }
}
