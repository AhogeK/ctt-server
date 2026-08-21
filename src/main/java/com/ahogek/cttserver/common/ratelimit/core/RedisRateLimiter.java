package com.ahogek.cttserver.common.ratelimit.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Redis-based rate limiter using Lua script for atomic operations.
 *
 * <p>Implements fixed window algorithm with atomic increment and expiration. The Lua script ensures
 * thread-safe operations in distributed environments, preventing race conditions.
 *
 * <p>Algorithm: Increment first, then check if limit exceeded. This prevents the race condition
 * where two concurrent requests both read the same counter value and both pass the check. When the
 * request is denied the script also returns the remaining TTL of the counter key so the caller can
 * populate a {@code Retry-After} hint in a single round-trip.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> rateLimitScript;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        // Lua script for atomic rate limiting with TTL reporting.
        // KEYS[1] = redis key
        // ARGV[1] = limit (max requests)
        // ARGV[2] = windowSeconds (TTL)
        // Returns: {1, 0} when allowed, {0, ttl} when blocked (ttl may be -1/-2 if absent).
        String lua =
                """
                local current = redis.call('incr', KEYS[1])
                if tonumber(current) == 1 then
                    redis.call('expire', KEYS[1], tonumber(ARGV[2]))
                end
                if tonumber(current) > tonumber(ARGV[1]) then
                    return {0, redis.call('ttl', KEYS[1])}
                end
                return {1, 0}
                """;

        this.rateLimitScript = new DefaultRedisScript<>(lua, List.class);
    }

    /**
     * Checks the rate limit and reports the remaining window when blocked.
     *
     * <p>The Lua script atomically increments the counter, sets the TTL on the first hit, and
     * returns both the allow/deny flag and the remaining seconds until the key expires. When the
     * request is allowed the TTL component is {@code 0}; when denied it is the Redis {@code TTL} of
     * the counter key, which may be non-positive ({@code -1} for no expiry, {@code -2} for a
     * missing key) if Redis state is inconsistent.
     *
     * @param key the Redis key for the rate limit counter
     * @param limit the maximum number of requests allowed
     * @param windowSeconds the time window in seconds
     * @return a {@link RateLimitResult} carrying the allow decision and remaining seconds; never
     *     {@code null}
     */
    public RateLimitResult checkLimit(String key, int limit, int windowSeconds) {
        // Spring's RedisTemplate#execute erases the element type of the returned list, so the raw
        // List is coerced element-by-element. This is the narrowest unchecked boundary: the Lua
        // script guarantees a two-element list of integers.
        @SuppressWarnings("unchecked")
        List<Long> result =
                (List<Long>)
                        redisTemplate.execute(
                                rateLimitScript,
                                Collections.singletonList(key),
                                String.valueOf(limit),
                                String.valueOf(windowSeconds));

        if (result == null || result.size() < 2) {
            return RateLimitResult.rejected(0L);
        }

        long allowed = result.getFirst();
        long ttl = result.get(1);
        return allowed == 1L ? RateLimitResult.permitted() : RateLimitResult.rejected(ttl);
    }
}
