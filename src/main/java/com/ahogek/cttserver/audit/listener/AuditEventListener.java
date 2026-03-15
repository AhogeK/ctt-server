package com.ahogek.cttserver.audit.listener;

import com.ahogek.cttserver.audit.SecurityAuditEvent;
import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.common.context.RequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Asynchronous listener for security audit events.
 *
 * <p>Architecture Guarantees:
 *
 * <ul>
 *   <li>Async: Uses {@code @Async} to prevent I/O blocking on the main request thread
 *   <li>Isolation: {@code Propagation.REQUIRES_NEW} ensures audit persistence is independent of the
 *       main business transaction
 *   <li>Fault Tolerance: All exceptions are caught and logged - audit failure never impacts
 *       business operations
 * </ul>
 *
 * <p>Event Mapping:
 *
 * <pre>
 * SecurityAuditEvent (errorCode, message, requestInfo) → AuditLog (action, resourceType, details, ipAddress, userAgent)
 * </pre>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @see com.ahogek.cttserver.audit.SecurityAuditEvent
 * @see com.ahogek.cttserver.audit.entity.AuditLog
 * @since 2026-03-16
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);
    private static final String UNKNOWN = "UNKNOWN";

    private final AuditLogRepository auditLogRepository;

    public AuditEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Handles security audit events asynchronously.
     *
     * <p>Maps the event to an {@link AuditLog} entity and persists it. The operation is:
     *
     * <ul>
     *   <li>Asynchronous (via @Async)
     *   <li>In a new transaction (via REQUIRES_NEW)
     *   <li>Failure-tolerant (exceptions are caught, not propagated)
     * </ul>
     *
     * @param event the security audit event to persist
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSecurityAudit(SecurityAuditEvent event) {
        try {
            AuditLog auditLog = mapToAuditLog(event);
            auditLogRepository.save(auditLog);

            log.atDebug()
                .addKeyValue("action", event.errorCode())
                .addKeyValue(
                    "ip",
                    Optional.ofNullable(event.requestInfo())
                        .map(RequestInfo::clientIp)
                        .orElse(UNKNOWN))
                .log("Audit log persisted asynchronously");

        } catch (Exception ex) {
            // Critical: Never let audit failures break business logic
            log.atError()
                .setCause(ex)
                .addKeyValue("errorCode", event.errorCode())
                .log("Failed to persist audit log - continuing without error propagation");
        }
    }

    /**
     * Maps a SecurityAuditEvent to an AuditLog entity.
     *
     * @param event the source event
     * @return the mapped audit log entity
     */
    private AuditLog mapToAuditLog(SecurityAuditEvent event) {
        // Build details map with all relevant context
        Map<String, Object> details = new HashMap<>();
        details.put("errorCode", event.errorCode().name());
        details.put("errorMessage", event.errorCode().message());

        if (event.message() != null) {
            details.put("customMessage", event.message());
        }

        if (event.timestamp() != null) {
            details.put("eventTimestamp", event.timestamp().toString());
        }

        // Extract request context if available
        String ipAddress = UNKNOWN;
        String userAgent = UNKNOWN;
        String resourceId = null;

        if (event.requestInfo() != null) {
            ipAddress = event.requestInfo().clientIp();
            userAgent = event.requestInfo().userAgent();
            resourceId = event.requestInfo().traceId();

            // Add request details to JSONB
            details.put("traceId", event.requestInfo().traceId());
            details.put("requestUri", event.requestInfo().requestUri());
            details.put("httpMethod", event.requestInfo().method());

            if (event.requestInfo().deviceId() != null) {
                details.put("deviceId", event.requestInfo().deviceId());
            }
        }

        return new AuditLog()
            .setAction("SECURITY_VIOLATION")
            .setResourceType(event.errorCode().name())
            .setResourceId(resourceId)
            .setDetails(details)
            .setIpAddress(ipAddress)
            .setUserAgent(userAgent);
    }
}
