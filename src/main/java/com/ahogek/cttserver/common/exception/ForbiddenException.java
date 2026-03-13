package com.ahogek.cttserver.common.exception;

/**
 * Exception for authorization errors (403 Forbidden).
 *
 * <p>Used when user lacks permission to access a resource.</p>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public final class ForbiddenException extends BusinessException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
