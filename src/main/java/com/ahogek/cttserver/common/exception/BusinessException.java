package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.common.response.ErrorResponse;

/**
 * Base exception for business-related errors.
 *
 * <p>All application exceptions should extend this class and provide an ErrorCode for consistent
 * error handling.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public abstract sealed class BusinessException extends RuntimeException
        permits ValidationException,
                UnauthorizedException,
                ForbiddenException,
                ConflictException,
                TooManyRequestsException,
                NotFoundException {

    private final ErrorCode errorCode;
    private final String customMessage;

    protected BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    protected BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage : errorCode.message());
        this.errorCode = errorCode;
        this.customMessage = customMessage;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorResponse toErrorResponse() {
        return customMessage != null
                ? ErrorResponse.of(errorCode, customMessage)
                : ErrorResponse.of(errorCode);
    }
}
