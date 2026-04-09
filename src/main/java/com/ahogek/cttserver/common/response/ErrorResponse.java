package com.ahogek.cttserver.common.response;

import com.ahogek.cttserver.common.exception.ErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Standardized error response for API errors.
 *
 * <p>This class provides detailed error information following RFC 7807 (Problem Details for HTTP
 * APIs) specification.
 *
 * <p>Response format:
 *
 * <pre>
 * {
 *   "code": "VALIDATION_ERROR",
 *   "message": "Invalid request parameters",
 *   "details": [
 *     {
 *       "field": "email",
 *       "message": "Invalid email format"
 *     }
 *   ],
 *   "traceId": "abc-123-def",
 *   "timestamp": "2026-03-14T03:23:12Z"
 * }
 * </pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> details,
        String traceId,
        Integer httpStatus,
        Instant timestamp,
        Instant retryAfter) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.message(),
                null,
                null,
                errorCode.httpStatus().value(),
                Instant.now(),
                null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String customMessage) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.message(customMessage),
                null,
                null,
                errorCode.httpStatus().value(),
                Instant.now(),
                null);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, null, null, Instant.now(), null);
    }

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, null, traceId, null, Instant.now(), null);
    }

    public ErrorResponse withCode(String code) {
        return new ErrorResponse(
                code, message, details, traceId, httpStatus, timestamp, retryAfter);
    }

    public ErrorResponse withMessage(String message) {
        return new ErrorResponse(
                code, message, details, traceId, httpStatus, timestamp, retryAfter);
    }

    public ErrorResponse withTraceId(String traceId) {
        return new ErrorResponse(
                code, message, details, traceId, httpStatus, timestamp, retryAfter);
    }

    public ErrorResponse withHttpStatus(Integer httpStatus) {
        return new ErrorResponse(
                code, message, details, traceId, httpStatus, timestamp, retryAfter);
    }

    public ErrorResponse withDetails(List<FieldError> details) {
        return new ErrorResponse(
                code, message, details, traceId, httpStatus, timestamp, retryAfter);
    }

    public ErrorResponse withRetryAfter(Instant retryAfter) {
        return new ErrorResponse(
                code, message, details, traceId, httpStatus, timestamp, retryAfter);
    }

    public ErrorResponse addDetail(String field, String message) {
        List<FieldError> newDetails =
                this.details != null ? new ArrayList<>(this.details) : new ArrayList<>();
        newDetails.add(new FieldError(field, message));
        return new ErrorResponse(
                code, message, newDetails, traceId, httpStatus, timestamp, retryAfter);
    }
}
