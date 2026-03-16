package com.ahogek.cttserver.audit;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.common.context.RequestInfo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Strongly typed security audit event.
 *
 * <p>Represents an audit event as a five-tuple model: User, Action, Resource, Severity,
 * Environment.
 *
 * <p>Event Structure:
 *
 * <ul>
 *   <li>User: Subject who triggered the event (null if not logged in)
 *   <li>Action: What was done (AuditAction enum)
 *   <li>Resource: Target resource type and ID
 *   <li>Severity: Risk level for alerting (INFO/WARNING/CRITICAL)
 *   <li>Environment: IP, UA, TraceId and other context
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 * @see com.ahogek.cttserver.audit.entity.AuditLog
 * @see com.ahogek.cttserver.audit.listener.AuditEventListener
 */
public record SecurityAuditEvent(
        UUID userId,
        AuditAction action,
        ResourceType resourceType,
        String resourceId,
        SecuritySeverity severity,
        String ipAddress,
        String userAgent,
        Map<String, Object> details,
        Instant timestamp) {

    /**
     * Creates a security audit event with current timestamp.
     *
     * @param userId the subject who triggered the event
     * @param action the audit action
     * @param resourceType the target resource type
     * @param resourceId the target resource identifier
     * @param severity the risk severity level
     * @param ipAddress the client IP address
     * @param userAgent the client user agent
     * @param details additional contextual details for JSONB storage
     */
    public SecurityAuditEvent(
            UUID userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            SecuritySeverity severity,
            String ipAddress,
            String userAgent,
            Map<String, Object> details) {
        this(
                userId,
                action,
                resourceType,
                resourceId,
                severity,
                ipAddress,
                userAgent,
                details,
                Instant.now());
    }

    /**
     * Convenience constructor for security violations from exception handling.
     *
     * @param action the security action (e.g., UNAUTHORIZED_ACCESS)
     * @param resourceType the target resource type
     * @param severity the severity level
     * @param requestInfo the HTTP request context
     * @param details additional details
     */
    public SecurityAuditEvent(
            AuditAction action,
            ResourceType resourceType,
            SecuritySeverity severity,
            RequestInfo requestInfo,
            Map<String, Object> details) {
        this(
                null,
                action,
                resourceType,
                requestInfo != null ? requestInfo.traceId() : null,
                severity,
                requestInfo != null ? requestInfo.clientIp() : null,
                requestInfo != null ? requestInfo.userAgent() : null,
                details,
                Instant.now());
    }
}
