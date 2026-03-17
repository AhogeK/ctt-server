package com.ahogek.cttserver.common.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Declares idempotent constraint for an API endpoint to prevent duplicate submissions.
 *
 * <p>Uses distributed locks (e.g., Redis) combined with a unique key to ensure a specific operation
 * is only executed once within a given time window.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @PostMapping("/sync")
 * @Idempotent(key = "#request.sessionId", expire = 5, unit = TimeUnit.MINUTES)
 * public ApiResponse<Void> syncData(@RequestBody SyncRequest request) { ... }
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * SpEL expression to dynamically resolve the idempotency key from method arguments.
     *
     * <p>If empty, falls back to a combination of: UserId + Method Name + Request URI.
     *
     * @return the SpEL expression
     */
    String key() default "";

    /**
     * The expiration time for the idempotency lock.
     *
     * <p>Defines how long a duplicate request will be blocked after the first one.
     *
     * @return lock expiration duration
     */
    long expire() default 10;

    /**
     * The time unit for the expiration time.
     *
     * @return the time unit
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}
