package com.ahogek.cttserver.audit;

import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ErrorCode;

import java.time.Instant;

/**
 * Security audit event for authentication/authorization failures.
 *
 * <p>Published when security violations occur (unauthorized access, forbidden resources, etc.).
 * These events are consumed by audit loggers and SIEM systems for security monitoring.
 *
 * <p><strong>Event Fields:</strong>
 *
 * <ul>
 *   <li>errorCode - The specific security error code
 *   <li>message - Human-readable violation description
 *   <li>requestInfo - HTTP request context (IP, URI, user agent)
 *   <li>timestamp - Event occurrence time (UTC)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public record SecurityAuditEvent(
    ErrorCode errorCode, String message, RequestInfo requestInfo, Instant timestamp) {

    /**
     * Creates a security audit event with current timestamp.
     *
     * @param errorCode   the security error code
     * @param message     the violation message
     * @param requestInfo the HTTP request context
     */
    public SecurityAuditEvent(ErrorCode errorCode, String message, RequestInfo requestInfo) {
        this(errorCode, message, requestInfo, Instant.now());
    }
}
