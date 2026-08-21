package com.ahogek.cttserver.common.ratelimit.core;

/**
 * Immutable result of a rate-limit check.
 *
 * <p>Carries both the allow/deny decision and, when denied, the remaining seconds until the fixed
 * window resets. The TTL is sourced atomically from the Redis Lua script to avoid a follow-up
 * round-trip and the inherent race between a separate {@code TTL} call and the increment.
 *
 * @param allowed {@code true} when the request is under the limit, {@code false} when blocked
 * @param remainingSeconds seconds until the counter key expires; {@code 0} when allowed, may be
 *     non-positive ({@code -1}/{@code -2}) when Redis reports no TTL on the key - callers should
 *     treat non-positive values as "unknown" and omit the retry hint
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-21
 */
public record RateLimitResult(boolean allowed, long remainingSeconds) {

    /**
     * Factory for a permitted request.
     *
     * @return a permitted result with zero remaining seconds
     */
    public static RateLimitResult permitted() {
        return new RateLimitResult(true, 0L);
    }

    /**
     * Factory for a denied request.
     *
     * @param remainingSeconds seconds until the window resets; pass a non-positive value when the
     *     TTL is unknown so the caller omits the retry hint
     * @return a denied result carrying the remaining seconds
     */
    public static RateLimitResult rejected(long remainingSeconds) {
        return new RateLimitResult(false, remainingSeconds);
    }
}
