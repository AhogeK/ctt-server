package com.ahogek.cttserver.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for multiple {@link RateLimit} declarations.
 *
 * <p>Allows declaring multiple rate limits on a single method for different dimensions (e.g.,
 * IP-based and email-based limits simultaneously).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @RateLimit(type = RateLimitType.EMAIL, keyExpression = "#request.email", limit = 3, windowSeconds = 600)
 * @RateLimit(type = RateLimitType.IP, limit = 30, windowSeconds = 3600)
 * @PostMapping("/forgot-password")
 * public ApiResponse<EmptyResponse> forgotPassword(...) { ... }
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-08
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimits {
    RateLimit[] value();
}
