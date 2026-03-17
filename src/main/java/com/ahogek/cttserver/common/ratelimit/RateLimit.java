package com.ahogek.cttserver.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares rate limiting constraints for an API endpoint.
 *
 * <p>Applied at the controller method level. Uses AOP to enforce limits before the business logic
 * executes.
 *
 * <p>Supports four dimensions: IP, USER, EMAIL (with SpEL), and API (global).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @PostMapping("/login")
 * @RateLimit(type = RateLimitType.IP, limit = 10, windowSeconds = 300)
 * public ApiResponse<Token> login(...) { ... }
 *
 * @PostMapping("/send-verification")
 * @RateLimit(type = RateLimitType.EMAIL, keyExpression = "#request.email", limit = 3, windowSeconds = 60)
 * public void sendVerificationEmail(...) { ... }
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * The isolation dimension for the rate limit.
     *
     * @return rate limit type
     */
    RateLimitType type() default RateLimitType.IP;

    /**
     * SpEL expression to extract dynamic key value.
     *
     * <p>Required when type is EMAIL to extract email from request parameters. Supports SpEL
     * expressions like "#request.email" or "#email".
     *
     * @return SpEL expression for key extraction
     */
    String keyExpression() default "";

    /**
     * The maximum number of allowed requests within the time window.
     *
     * @return request limit
     */
    int limit();

    /**
     * The time window in seconds.
     *
     * @return window size in seconds
     */
    int windowSeconds();
}
