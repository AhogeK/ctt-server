package com.ahogek.cttserver.common.exception;

/**
 * Exception for not found errors (404 Not Found).
 *
 * <p>Used when requested resource does not exist.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public final class NotFoundException extends BusinessException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
