package com.ahogek.cttserver.common.exception;

/**
 * Exception for conflict errors (409 Conflict).
 *
 * <p>Used when attempting to create a resource that already exists.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public final class ConflictException extends BusinessException {

    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConflictException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
