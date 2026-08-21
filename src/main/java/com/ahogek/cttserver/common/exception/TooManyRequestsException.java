package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.common.response.ErrorResponse;

import java.time.Instant;

/**
 * Exception for rate limit errors (429 Too Many Requests).
 *
 * <p>Used when a client exceeds the allowed request quota. Carries an optional {@code retryAfter}
 * timestamp indicating when the caller may retry; when present the global exception handler emits
 * both the response body field and the HTTP {@code Retry-After} header (delta-seconds per RFC
 * 7231).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public final class TooManyRequestsException extends BusinessException {

    private final Instant retryAfter;

    public TooManyRequestsException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public TooManyRequestsException(ErrorCode errorCode, String customMessage) {
        this(errorCode, customMessage, null);
    }

    /**
     * Constructs a rate-limit exception carrying the absolute retry timestamp.
     *
     * @param errorCode the {@link ErrorCode} (typically {@code RATE_LIMIT_001} or {@code MAIL_004})
     * @param customMessage override message; when {@code null} the error code's default message is
     *     used
     * @param retryAfter absolute instant the caller may retry, or {@code null} when unknown (e.g.
     *     Redis TTL unavailable)
     */
    public TooManyRequestsException(ErrorCode errorCode, String customMessage, Instant retryAfter) {
        super(errorCode, customMessage);
        this.retryAfter = retryAfter;
    }

    /**
     * @return the absolute retry instant, or {@code null} when no hint is available
     */
    public Instant retryAfter() {
        return retryAfter;
    }

    @Override
    public ErrorResponse toErrorResponse() {
        return super.toErrorResponse().withRetryAfter(retryAfter);
    }
}
