package com.ahogek.cttserver.audit.service;

import com.ahogek.cttserver.audit.SecurityAuditEvent;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Facade service for audit logging.
 *
 * <p>Encapsulates ApplicationEventPublisher and RequestContext extraction details. Provides
 * high-level semantic APIs for business layer to record audit trails safely and conveniently.
 *
 * <p>Context Extraction:
 *
 * <ul>
 *   <li>Automatically extracts IP and User-Agent from RequestContext when available
 *   <li>Falls back to SYSTEM/INTERNAL for non-web contexts (e.g., scheduled tasks)
 * </ul>
 *
 * <p>Usage Example:
 *
 * <pre>{@code
 * // Log authentication failure
 * auditLog.logFailure(userId, AuditAction.LOGIN_FAILED, ResourceType.USER,
 *                     userId.toString(), "Invalid password");
 *
 * // Log state transition
 * auditLog.logTransition(userId, AuditAction.PASSWORD_CHANGED, ResourceType.USER,
 *                        userId.toString(), oldHash, newHash);
 * }</pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @see com.ahogek.cttserver.audit.SecurityAuditEvent
 * @see com.ahogek.cttserver.audit.listener.AuditEventListener
 * @since 2026-03-16
 */
@Service
public class AuditLogService {

    private static final String SYSTEM_IP = "SYSTEM";
    private static final String INTERNAL_UA = "INTERNAL";

    private final ApplicationEventPublisher eventPublisher;

    public AuditLogService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Core audit logging method with full parameter control.
     *
     * <p>Automatically extracts request context (IP, User-Agent) from RequestContext when
     * available. Falls back to SYSTEM/INTERNAL for non-web contexts.
     *
     * @param userId the user who triggered the action (null for anonymous)
     * @param action the audit action
     * @param resourceType the type of resource affected
     * @param resourceId the identifier of the affected resource
     * @param severity the security severity level
     * @param details structured audit details
     */
    public void log(
            UUID userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            SecuritySeverity severity,
            AuditDetails details) {

        String ipAddress = SYSTEM_IP;
        String userAgent = INTERNAL_UA;
        String traceId = null;

        var requestOpt = RequestContext.current();
        if (requestOpt.isPresent()) {
            RequestInfo req = requestOpt.get();
            ipAddress = req.clientIp();
            userAgent = req.userAgent();
            traceId = req.traceId();
        }

        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        userId,
                        action,
                        resourceType,
                        resourceId,
                        severity,
                        ipAddress,
                        userAgent,
                        traceId,
                        details);

        eventPublisher.publishEvent(event);
    }

    /**
     * Records successful operation at INFO severity.
     *
     * @param userId the user who performed the action
     * @param action the audit action
     * @param resourceType the resource type
     * @param resourceId the resource identifier
     */
    public void logSuccess(
            UUID userId, AuditAction action, ResourceType resourceType, String resourceId) {
        log(userId, action, resourceType, resourceId, SecuritySeverity.INFO, AuditDetails.empty());
    }

    /**
     * Records failed operation at WARNING severity.
     *
     * @param userId the user who triggered the failure (null if unknown)
     * @param action the audit action
     * @param resourceType the resource type
     * @param resourceId the resource identifier
     * @param reason the failure reason
     */
    public void logFailure(
            UUID userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            String reason) {
        log(
                userId,
                action,
                resourceType,
                resourceId,
                SecuritySeverity.WARNING,
                AuditDetails.reason(reason));
    }

    /**
     * Records critical security event at CRITICAL severity.
     *
     * @param userId the user involved (null if unknown)
     * @param action the audit action
     * @param resourceType the resource type
     * @param resourceId the resource identifier
     * @param details structured details for the critical event
     */
    public void logCritical(
            UUID userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            AuditDetails details) {
        log(userId, action, resourceType, resourceId, SecuritySeverity.CRITICAL, details);
    }

    /**
     * Records state transition with before/after snapshot.
     *
     * <p>Uses INFO severity. Suitable for tracking data changes like password updates, config
     * modifications.
     *
     * @param userId the user who made the change
     * @param action the audit action
     * @param resourceType the resource type
     * @param resourceId the resource identifier
     * @param stateBefore the state before change (JSON string or simple value)
     * @param stateAfter the state after change (JSON string or simple value)
     */
    public void logTransition(
            UUID userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            String stateBefore,
            String stateAfter) {
        log(
                userId,
                action,
                resourceType,
                resourceId,
                SecuritySeverity.INFO,
                AuditDetails.transition(stateBefore, stateAfter));
    }
}
