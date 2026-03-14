package com.ahogek.cttserver.common.exception;

import java.util.UUID;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ahogek.cttserver.common.response.ErrorResponse;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Business exception: {} - {}", traceId, ex.errorCode(), ex.getMessage());

        ErrorResponse response = ex.toErrorResponse().withTraceId(traceId);

        HttpStatus status = HttpStatus.valueOf(response.httpStatus());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Validation error", traceId);

        var details =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new ErrorResponse.FieldError(
                                                error.getField(), error.getDefaultMessage()))
                        .toList();

        ErrorResponse response =
                ErrorResponse.of(ErrorCode.COMMON_003.name(), ErrorCode.COMMON_003.message())
                        .withTraceId(traceId)
                        .withHttpStatus(ErrorCode.COMMON_003.httpStatus().value())
                        .withDetails(details);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Constraint violation", traceId);

        var details =
                ex.getConstraintViolations().stream()
                        .map(
                                v ->
                                        new ErrorResponse.FieldError(
                                                v.getPropertyPath().toString(), v.getMessage()))
                        .toList();

        ErrorResponse response =
                ErrorResponse.of(ErrorCode.COMMON_003.name(), ErrorCode.COMMON_003.message())
                        .withTraceId(traceId)
                        .withHttpStatus(ErrorCode.COMMON_003.httpStatus().value())
                        .withDetails(details);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Missing parameter: {}", traceId, ex.getParameterName());

        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_005).withTraceId(traceId);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Malformed request body", traceId);

        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_001).withTraceId(traceId);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Illegal argument: {}", traceId, ex.getMessage());

        ErrorResponse response =
                ErrorResponse.of(ErrorCode.COMMON_001, ex.getMessage()).withTraceId(traceId);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
            InternalServerErrorException ex) {
        String traceId = generateTraceId();
        log.error("[{}] Internal server error: {}", traceId, ex.getMessage());

        ErrorResponse response =
                ErrorResponse.of(ErrorCode.SYSTEM_001, ex.getMessage()).withTraceId(traceId);

        return ResponseEntity.internalServerError().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String traceId = generateTraceId();
        log.error("[{}] Unexpected error", traceId, ex);

        ErrorResponse response = ErrorResponse.of(ErrorCode.SYSTEM_001).withTraceId(traceId);

        return ResponseEntity.internalServerError().body(response);
    }
}
