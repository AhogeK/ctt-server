package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.common.response.ErrorResponse;
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

import java.util.UUID;

/**
 * Global exception handler for REST API.
 *
 * <p>Converts all BusinessException and Spring exceptions to standardized ErrorResponse.</p>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
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

        ErrorResponse response = ex.toErrorResponse()
                .toBuilder()
                .traceId(traceId)
                .build();

        HttpStatus status = HttpStatus.valueOf(response.httpStatus());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Validation error", traceId);

        ErrorResponse.Builder builder = ErrorResponse.builder()
                .code(ErrorCode.COMMON_003.name())
                .message(ErrorCode.COMMON_003.message())
                .httpStatus(ErrorCode.COMMON_003.httpStatus().value())
                .traceId(traceId);

        ex.getBindingResult().getFieldErrors().forEach(error ->
                builder.addDetail(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(builder.build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Constraint violation", traceId);

        ErrorResponse.Builder builder = ErrorResponse.builder()
                .code(ErrorCode.COMMON_003.name())
                .message(ErrorCode.COMMON_003.message())
                .httpStatus(ErrorCode.COMMON_003.httpStatus().value())
                .traceId(traceId);

        ex.getConstraintViolations().forEach(violation ->
                builder.addDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()));

        return ResponseEntity.badRequest().body(builder.build());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Missing parameter: {}", traceId, ex.getParameterName());

        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_005)
                .toBuilder()
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Malformed request body", traceId);

        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_001)
                .toBuilder()
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Illegal argument: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_001, ex.getMessage())
                .toBuilder()
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String traceId = generateTraceId();
        log.error("[{}] Unexpected error", traceId, ex);

        ErrorResponse response = ErrorResponse.of(ErrorCode.SYSTEM_001)
                .toBuilder()
                .traceId(traceId)
                .build();

        return ResponseEntity.internalServerError().body(response);
    }
}
