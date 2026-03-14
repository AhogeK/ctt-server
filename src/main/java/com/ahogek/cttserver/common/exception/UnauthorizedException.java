package com.ahogek.cttserver.common.exception;

/**
 * Exception for authentication errors (401 Unauthorized).
 *
 * <p>Used when authentication fails or token is invalid/expired.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public final class UnauthorizedException extends BusinessException {

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public UnauthorizedException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
