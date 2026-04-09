package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.common.response.ErrorResponse;

import java.time.Instant;

/**
 * Exception thrown when an account is temporarily locked due to too many failed login attempts.
 *
 * <p>Includes a {@code retryAfter} timestamp indicating when the user can attempt login again.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-10
 */
public final class AccountLockedException extends BusinessException {

    private final Instant retryAfter;

    public AccountLockedException(ErrorCode errorCode, Instant retryAfter) {
        super(errorCode);
        this.retryAfter = retryAfter;
    }

    public AccountLockedException(ErrorCode errorCode, String message, Instant retryAfter) {
        super(errorCode, message);
        this.retryAfter = retryAfter;
    }

    public Instant retryAfter() {
        return retryAfter;
    }

    @Override
    public ErrorResponse toErrorResponse() {
        ErrorResponse base = super.toErrorResponse();
        return base.withRetryAfter(retryAfter);
    }
}
