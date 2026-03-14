package com.ahogek.cttserver.common.exception;

/**
 * Exception for rate limit errors (429 Too Many Requests).
 *
 * <p>Used when client exceeds allowed request quota.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public final class TooManyRequestsException extends BusinessException {

    public TooManyRequestsException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TooManyRequestsException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
