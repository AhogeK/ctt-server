package com.ahogek.cttserver.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Declares rate limiting constraints for an API endpoint.
 *
 * <p>Applied at the controller method or class level. Uses AOP/Interceptor to enforce limits before
 * the business logic executes.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @PostMapping("/login")
 * @RateLimit(key = "login", capacity = 5, period = 1, unit = TimeUnit.MINUTES, type = RateLimitType.IP)
 * public ApiResponse<Token> login(...) { ... }
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * Unique key prefix for the rate limit bucket.
     *
     * <p>If not specified, the system will auto-generate one based on ClassName.methodName.
     *
     * @return the cache key prefix
     */
    String key() default "";

    /**
     * The maximum number of allowed requests within the defined period.
     *
     * @return bucket capacity
     */
    int capacity();

    /**
     * The time period during which the capacity applies.
     *
     * @return the time window
     */
    long period();

    /**
     * The time unit for the defined period.
     *
     * @return the time unit
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * The isolation dimension for the rate limit.
     *
     * @return rate limit type
     */
    RateLimitType type() default RateLimitType.USER;
}
