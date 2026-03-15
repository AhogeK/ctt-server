package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Global exception handler with structured logging.
 *
 * <p><strong>Error Log Architecture:</strong>
 *
 * <ul>
 *   <li>Business exceptions (4xx): WARN level, no stack trace (expected behavior)
 *   <li>System errors (5xx): ERROR level, full stack trace (unexpected failures)
 *   <li>Single exit point: All errors logged here, never in controllers/services
 * </ul>
 *
 * <p><strong>Design Principles:</strong>
 *
 * <ul>
 *   <li>Never swallow exceptions: Always include cause in error logs
 *   <li>Structured logging: Use Fluent API with key-value pairs for machine parsing
 *   <li>Trace correlation: All logs include traceId for distributed tracing
 *   <li>Performance: Business exceptions don't print stack traces (reduces I/O overhead)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_CODE_KEY = "error_code";
    private static final String ERROR_TYPE_KEY = "error_type";
    private static final String TRACE_ID_KEY = "trace_id";
    private static final String HTTP_STATUS_KEY = "http_status";
    private static final String FIELD_COUNT_KEY = "field_count";
    private static final String VIOLATION_COUNT_KEY = "violation_count";
    private static final String PARAMETER_NAME_KEY = "parameter_name";
    private static final String PARAMETER_TYPE_KEY = "parameter_type";
    private static final String EXCEPTION_CLASS_KEY = "exception_class";

    private static String currentTraceId() {
        return RequestContext.current()
                .map(RequestInfo::traceId)
                .orElseGet(
                        () -> {
                            String id = MDC.get("traceId");
                            return (id != null && !id.isBlank())
                                    ? id
                                    : UUID.randomUUID().toString().replace("-", "");
                        });
    }

    /**
     * Handles business exceptions - expected domain errors.
     *
     * <p>Logged at WARN level without stack trace (performance optimization). These are not system
     * failures but domain rule violations.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        String traceId = currentTraceId();

        log.atWarn()
            .addKeyValue(ERROR_CODE_KEY, ex.errorCode().name())
            .addKeyValue(ERROR_TYPE_KEY, "BUSINESS_EXCEPTION")
            .addKeyValue(HTTP_STATUS_KEY, ex.errorCode().httpStatus().value())
            .addKeyValue(TRACE_ID_KEY, traceId)
            .log("Business exception: {}", ex.getMessage());

        ErrorResponse response = ex.toErrorResponse().withTraceId(traceId);
        HttpStatus status = HttpStatus.valueOf(response.httpStatus());
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Handles validation errors from @Valid annotated request bodies.
     *
     * <p>Logged at WARN level with field-level details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        String traceId = currentTraceId();

        var fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new ErrorResponse.FieldError(
                                                error.getField(), error.getDefaultMessage()))
                        .toList();

        log.atWarn()
            .addKeyValue(ERROR_CODE_KEY, ErrorCode.COMMON_003.name())
            .addKeyValue(ERROR_TYPE_KEY, "VALIDATION_ERROR")
            .addKeyValue(FIELD_COUNT_KEY, fieldErrors.size())
            .addKeyValue(TRACE_ID_KEY, traceId)
            .log("Request validation failed: {} field(s) invalid", fieldErrors.size());

        ErrorResponse response =
                ErrorResponse.of(ErrorCode.COMMON_003.name(), ErrorCode.COMMON_003.message())
                        .withTraceId(traceId)
                        .withHttpStatus(ErrorCode.COMMON_003.httpStatus().value())
                    .withDetails(fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles constraint violations from @Validated parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        String traceId = currentTraceId();

        var violations =
                ex.getConstraintViolations().stream()
                        .map(
                                v ->
                                        new ErrorResponse.FieldError(
                                                v.getPropertyPath().toString(), v.getMessage()))
                    .toList();

        log.atWarn()
            .addKeyValue(ERROR_CODE_KEY, ErrorCode.COMMON_003.name())
            .addKeyValue(ERROR_TYPE_KEY, "CONSTRAINT_VIOLATION")
            .addKeyValue(VIOLATION_COUNT_KEY, violations.size())
            .addKeyValue(TRACE_ID_KEY, traceId)
            .log("Constraint violation: {} violation(s)", violations.size());

        ErrorResponse response =
                ErrorResponse.of(ErrorCode.COMMON_003.name(), ErrorCode.COMMON_003.message())
                        .withTraceId(traceId)
                        .withHttpStatus(ErrorCode.COMMON_003.httpStatus().value())
                    .withDetails(violations);

        return ResponseEntity.badRequest().body(response);
    }

    /** Handles missing required request parameters. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex) {
        String traceId = currentTraceId();

        log.atWarn()
            .addKeyValue(ERROR_CODE_KEY, ErrorCode.COMMON_005.name())
            .addKeyValue(ERROR_TYPE_KEY, "MISSING_PARAMETER")
            .addKeyValue(PARAMETER_NAME_KEY, ex.getParameterName())
            .addKeyValue(PARAMETER_TYPE_KEY, ex.getParameterType())
            .addKeyValue(TRACE_ID_KEY, traceId)
            .log(
                "Missing required parameter: {} (type: {})",
                ex.getParameterName(),
                ex.getParameterType());

        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_005).withTraceId(traceId);
        return ResponseEntity.badRequest().body(response);
    }

    /** Handles malformed request bodies (JSON parse errors). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex) {
        String traceId = currentTraceId();

        log.atWarn()
            .addKeyValue(ERROR_CODE_KEY, ErrorCode.COMMON_001.name())
            .addKeyValue(ERROR_TYPE_KEY, "MALFORMED_REQUEST")
            .addKeyValue(TRACE_ID_KEY, traceId)
            .log("Malformed request body: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_001).withTraceId(traceId);
        return ResponseEntity.badRequest().body(response);
    }

    /** Handles illegal argument exceptions. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        String traceId = currentTraceId();

        log.atWarn()
            .addKeyValue(ERROR_CODE_KEY, ErrorCode.COMMON_001.name())
            .addKeyValue(ERROR_TYPE_KEY, "ILLEGAL_ARGUMENT")
            .addKeyValue(TRACE_ID_KEY, traceId)
            .log("Illegal argument: {}", ex.getMessage());

        ErrorResponse response =
                ErrorResponse.of(ErrorCode.COMMON_001, ex.getMessage()).withTraceId(traceId);
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles internal server errors - system failures requiring investigation.
     *
     * <p>Logged at ERROR level with FULL STACK TRACE. These indicate bugs or infrastructure issues.
     */
    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
            InternalServerErrorException ex) {
        String traceId = currentTraceId();

        // System-level errors must log full stack trace for debugging
        log.atError()
            .setCause(ex)
            .addKeyValue(ERROR_CODE_KEY, ErrorCode.SYSTEM_001.name())
            .addKeyValue(ERROR_TYPE_KEY, "INTERNAL_SERVER_ERROR")
            .addKeyValue(TRACE_ID_KEY, traceId)
            .log("Internal server error occurred: {}", ex.getMessage());

        ErrorResponse response =
                ErrorResponse.of(ErrorCode.SYSTEM_001, ex.getMessage()).withTraceId(traceId);
        return ResponseEntity.internalServerError().body(response);
    }

    /**
     * Fallback handler for all unhandled exceptions.
     *
     * <p>This is the safety net for unexpected errors (NPE, OOM, DB connection failures, etc.).
     * Logged at ERROR level with FULL STACK TRACE.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String traceId = currentTraceId();

        // Unknown/unexpected errors - always log full stack trace
        log.atError()
            .setCause(ex)
            .addKeyValue(ERROR_CODE_KEY, ErrorCode.SYSTEM_001.name())
            .addKeyValue(ERROR_TYPE_KEY, "UNHANDLED_EXCEPTION")
            .addKeyValue(EXCEPTION_CLASS_KEY, ex.getClass().getName())
            .addKeyValue(TRACE_ID_KEY, traceId)
            .log("Unexpected critical system error: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(ErrorCode.SYSTEM_001).withTraceId(traceId);
        return ResponseEntity.internalServerError().body(response);
    }
}
