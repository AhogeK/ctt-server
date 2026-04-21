package com.ahogek.cttserver.common.exception;

/** Exception for 502 Bad Gateway errors: upstream service failures. */
public final class BadGatewayException extends BusinessException {

    public BadGatewayException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BadGatewayException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
