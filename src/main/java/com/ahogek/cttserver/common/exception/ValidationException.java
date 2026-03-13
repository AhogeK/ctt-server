package com.ahogek.cttserver.common.exception;

/**
 * Exception for validation errors (400 Bad Request).
 *
 * <p>Used when request parameters or body fail validation.</p>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public final class ValidationException extends BusinessException {

    public ValidationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ValidationException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
