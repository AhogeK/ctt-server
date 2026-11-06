package com.ahogek.cttserver.common.ratelimit;

/**
 * Dimension type for rate limiting.
 *
 * <p>Determines how the rate limiting counter is isolated (e.g., per user, per IP).
 *
 * <p>The former {@code API} dimension — a bucket shared by every caller of one endpoint — was
 * removed on 2026-11-05. It turned an endpoint-level ceiling into a fleet-wide one, so a single
 * account could starve every other account. Per-account buckets ({@code USER}) plus the
 * installation-wide ceiling enforced by {@code GlobalRequestGuard} cover both needs without that
 * footgun.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
public enum RateLimitType {

    /**
     * Rate limit by client IP address.
     *
     * <p>Does not require authentication. Vulnerable to NAT/Proxy aggregation (multiple users
     * sharing one IP might get throttled together). Best for public endpoints (e.g., login,
     * registration).
     */
    IP,

    /**
     * Rate limit by authenticated user ID.
     *
     * <p>Requires user to be authenticated. Provides the most precise and fair limiting. Best for
     * business APIs (e.g., sync, profile update).
     */
    USER,

    /**
     * Rate limit by email extracted from request parameters via SpEL expression.
     *
     * <p>Best for preventing email verification bombing attacks. Requires keyExpression to extract
     * email from request.
     */
    EMAIL
}
