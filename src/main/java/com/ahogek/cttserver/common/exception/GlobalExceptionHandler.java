package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.audit.SecurityAuditEvent;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.response.ErrorResponse;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Global exception handler with three-level logging strategy.
 *
 * <p><strong>Three-Level Exception Logging Strategy:</strong>
 *
 * <ul>
 *   <li><strong>ERROR (System):</strong> Unhandled exceptions (NPE, SQLException, etc.) - Full
 *       stack trace with {@code setCause(ex)} - Triggers critical alerts (PagerDuty/DingTalk) -
 *       Contributes to Error Rate SLA
 *   <li><strong>WARN (Business):</strong> Expected business exceptions (validation, state
 *       conflicts) - Structured logging WITHOUT stack trace (O(1) performance) - Tracks business
 *       funnel metrics
 *   <li><strong>AUDIT (Security):</strong> Security violations (unauthorized, forbidden) - INFO
 *       level with audit context (client_ip, target_uri) - Structured for SIEM/risk control systems
 *       - No stack trace to reduce noise
 * </ul>
 *
 * <p><strong>Performance Optimization:</strong>
 *
 * <p>By avoiding {@code setCause()} for expected exceptions (Business, Validation, Security), we
 * eliminate the O(D) stack trace capture overhead, reducing I/O by ~90% for high-frequency business
 * errors.
 *
 * <p><strong>Design Principles:</strong>
 *
 * <ul>
 *   <li>Never swallow exceptions: ERROR level always includes stack trace
 *   <li>Structured logging: Use Fluent API with key-value pairs for machine parsing
 *   <li>Trace correlation: All logs include trace_id for distributed tracing
 *   <li>Performance: Business/Audit exceptions don't print stack traces
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Structured logging field keys
    private static final String ERROR_CODE_KEY = "error_code";
    // Audit logging keys (for security events)
    private static final String AUDIT_EVENT_KEY = "audit_event";
    private static final String VIOLATION_TYPE_KEY = "violation_type";
    private static final String ERROR_TYPE_KEY = "error_type";
    private static final String TRACE_ID_KEY = "trace_id";
    private static final String HTTP_STATUS_KEY = "http_status";
    private static final String FIELD_COUNT_KEY = "field_count";
    private static final String VIOLATION_COUNT_KEY = "violation_count";
    private static final String PARAMETER_NAME_KEY = "parameter_name";
    private static final String PARAMETER_TYPE_KEY = "parameter_type";
    private static final String EXCEPTION_CLASS_KEY = "exception_class";
    private static final String CLIENT_IP_KEY = "client_ip";
    private static final String TARGET_URI_KEY = "target_uri";
    private final ApplicationEventPublisher eventPublisher;

    public GlobalExceptionHandler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

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
     * [LEVEL 1] System Exceptions - CRITICAL with full stack trace.
     *
     * <p>Catches all unhandled runtime exceptions (NPE, SQLException, OOM, etc.). These indicate
     * bugs or infrastructure failures requiring immediate attention.
     *
     * <p><strong>Log Strategy:</strong>
     *
     * <ul>
     *   <li>Level: ERROR
     *   <li>Stack Trace: Full (via {@code setCause(ex)})
     *   <li>Alerting: Triggers PagerDuty/DingTalk critical alerts
     *   <li>Metric: Contributes to Error Rate SLA
     * </ul>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleSystemException(Exception ex) {
        String traceId = currentTraceId();

        // Full stack trace for debugging critical issues
        log.atError()
                .setCause(ex)
                .addKeyValue(ERROR_CODE_KEY, ErrorCode.SYSTEM_001.name())
                .addKeyValue(ERROR_TYPE_KEY, "UNHANDLED_EXCEPTION")
                .addKeyValue(EXCEPTION_CLASS_KEY, ex.getClass().getName())
                .addKeyValue(TRACE_ID_KEY, traceId)
                .log("Critical system error occurred: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(ErrorCode.SYSTEM_001).withTraceId(traceId);
        return ResponseEntity.internalServerError().body(response);
    }

    /**
     * [LEVEL 1] Internal Server Error - Explicit system failure.
     *
     * <p>Handles InternalServerErrorException (wrapper for internal failures).
     *
     * <p><strong>Log Strategy:</strong>
     *
     * <ul>
     *   <li>Level: ERROR
     *   <li>Stack Trace: Full (via {@code setCause(ex)})
     * </ul>
     */
    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(
            InternalServerErrorException ex) {
        String traceId = currentTraceId();

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
     * [LEVEL 3] Security Audit Exceptions - INFO level for SIEM integration.
     *
     * <p>Handles authentication/authorization failures that require security audit logging. These
     * are expected security events, not system errors.
     *
     * <p><strong>Log Strategy:</strong>
     *
     * <ul>
     *   <li>Level: INFO
     *   <li>Stack Trace: None (performance optimized)
     *   <li>Context: Includes client_ip, target_uri for risk control analysis
     *   <li>Integration: Structured for SIEM/audit systems
     * </ul>
     *
     * <p><strong>Scenarios:</strong>
     *
     * <ul>
     *   <li>Unauthorized (401): Invalid/expired tokens
     *   <li>Forbidden (403): Permission denied, access control violations
     * </ul>
     */
    @ExceptionHandler({UnauthorizedException.class, ForbiddenException.class})
    public ResponseEntity<ErrorResponse> handleSecurityException(BusinessException ex) {
        String traceId = currentTraceId();
        RequestInfo requestInfo = RequestContext.current().orElse(null);

        // Audit logging without stack trace - structured for SIEM systems
        var logBuilder =
                log.atInfo()
                        .addKeyValue(AUDIT_EVENT_KEY, "SECURITY_VIOLATION")
                        .addKeyValue(VIOLATION_TYPE_KEY, ex.errorCode().name())
                        .addKeyValue(ERROR_CODE_KEY, ex.errorCode().name())
                        .addKeyValue(TRACE_ID_KEY, traceId);

        // Add request context if available
        if (requestInfo != null) {
            logBuilder =
                    logBuilder
                            .addKeyValue(CLIENT_IP_KEY, requestInfo.clientIp())
                            .addKeyValue(TARGET_URI_KEY, requestInfo.requestUri());
        }

        logBuilder.log("Security audit event: {}", ex.getMessage());

        // Publish async audit event for SIEM integration and persistent audit logging
        AuditAction action =
                ex instanceof UnauthorizedException
                        ? AuditAction.UNAUTHORIZED_ACCESS
                        : AuditAction.FORBIDDEN_ACCESS;
        SecuritySeverity severity = SecuritySeverity.WARNING;

        eventPublisher.publishEvent(
                new SecurityAuditEvent(
                        action,
                        ResourceType.UNKNOWN,
                        severity,
                        requestInfo,
                        java.util.Map.of(
                                "errorCode", ex.errorCode().name(),
                                "message", ex.getMessage())));

        ErrorResponse response = ex.toErrorResponse().withTraceId(traceId);
        return ResponseEntity.status(ex.errorCode().httpStatus()).body(response);
    }

    /**
     * [LEVEL 2] Business Exceptions - WARN level without stack trace.
     *
     * <p>Handles expected business rule violations (validation, state conflicts, etc.). These are
     * not system failures but normal business flow control.
     *
     * <p><strong>Log Strategy:</strong>
     *
     * <ul>
     *   <li>Level: WARN
     *   <li>Stack Trace: None (O(1) performance)
     *   <li>Metric: Tracks business funnel (e.g., validation failure rate)
     *   <li>Alerting: Should NOT trigger on-call alerts
     * </ul>
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        String traceId = currentTraceId();

        // No stack trace for expected business exceptions - performance critical
        log.atWarn()
                .addKeyValue(ERROR_CODE_KEY, ex.errorCode().name())
                .addKeyValue(ERROR_TYPE_KEY, "BUSINESS_EXCEPTION")
                .addKeyValue(HTTP_STATUS_KEY, ex.errorCode().httpStatus().value())
                .addKeyValue(TRACE_ID_KEY, traceId)
                .log("Business rule violation: {}", ex.getMessage());

        ErrorResponse response = ex.toErrorResponse().withTraceId(traceId);
        HttpStatus status = HttpStatus.valueOf(response.httpStatus());
        return ResponseEntity.status(status).body(response);
    }

    /**
     * [LEVEL 2] Validation Errors - WARN level without stack trace.
     *
     * <p>Handles request body validation failures from {@code @Valid} annotations.
     *
     * <p><strong>Log Strategy:</strong>
     *
     * <ul>
     *   <li>Level: WARN
     *   <li>Stack Trace: None
     *   <li>Context: Includes field-level error details
     * </ul>
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
     * [LEVEL 2] Constraint Violations - WARN level without stack trace.
     *
     * <p>Handles parameter validation failures from {@code @Validated}.
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

    /**
     * [LEVEL 2] Missing Parameters - WARN level without stack trace.
     *
     * <p>Handles missing required request parameters.
     */
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

    /**
     * [LEVEL 2] Malformed Request - WARN level without stack trace.
     *
     * <p>Handles JSON parse errors and unreadable request bodies.
     */
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

    /**
     * [LEVEL 2] Illegal Arguments - WARN level without stack trace.
     *
     * <p>Handles illegal argument exceptions from business logic.
     */
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
}
