package com.ahogek.cttserver.common.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares idempotent constraint for an API endpoint to prevent duplicate submissions.
 *
 * <p>Uses Redis distributed locks (SETNX) combined with a unique key to ensure a specific operation
 * is only executed once within a given time window.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @PostMapping("/register")
 * @Idempotent(
 *     prefix = "USER_REGISTER",
 *     keyExpression = "#request.email",
 *     includeUserId = false,
 *     expireSeconds = 5,
 *     message = "Registration is processing, please wait"
 * )
 * public ApiResponse<Void> register(@RequestBody UserRegisterRequest request) { ... }
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * Business prefix to distinguish different business scenarios.
     *
     * @return the business prefix
     */
    String prefix() default "";

    /**
     * SpEL expression to dynamically extract unique identifier from request parameters.
     *
     * <p>Example: "#request.email" or "#orderId"
     *
     * @return the SpEL expression for key extraction
     */
    String keyExpression() default "";

    /**
     * Whether to include current user ID in the idempotency key.
     *
     * <p>Default true to isolate concurrent requests from different users. Set to false for public
     * endpoints where user is not yet authenticated.
     *
     * @return true to include user ID in key
     */
    boolean includeUserId() default true;

    /**
     * Lock expiration time in seconds (prevents deadlock).
     *
     * <p>Defines how long the lock will be held. Default 5 seconds is suitable for most business
     * operations.
     *
     * @return lock expiration time in seconds
     */
    long expireSeconds() default 5;

    /**
     * Error message when duplicate request is detected.
     *
     * @return the conflict message
     */
    String message() default "Request is being processed, please do not resubmit";
}
