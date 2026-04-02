package com.ahogek.cttserver.audit.listener;

import com.ahogek.cttserver.audit.SecurityAuditEvent;
import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.repository.AuditLogRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
 *   <li>Timing: {@code @TransactionalEventListener(AFTER_COMMIT)} ensures audit runs only after the
 *       main transaction commits, avoiding FK constraint violations
 *   <li>Fault Tolerance: All exceptions are caught and logged - audit failure never impacts
 *       business operations
 * </ul>
 *
 * <p>Event Mapping:
 *
 * <pre>
 * SecurityAuditEvent (userId, action, resourceType, resourceId, severity, ip, ua, details)
 *   → AuditLog
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
     * Handles security audit events asynchronously after the main transaction commits.
     *
     * <p>Maps the event to an {@link AuditLog} entity and persists it. The operation is:
     *
     * <ul>
     *   <li>Triggered after main transaction commits (via @TransactionalEventListener)
     *   <li>Asynchronous (via @Async)
     *   <li>In a new transaction (via REQUIRES_NEW)
     *   <li>Failure-tolerant (exceptions are caught, not propagated)
     * </ul>
     *
     * @param event the security audit event to persist
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSecurityAudit(SecurityAuditEvent event) {
        try {
            AuditLog auditLog = mapToAuditLog(event);
            auditLogRepository.save(auditLog);

            log.atDebug()
                    .addKeyValue("action", event.action())
                    .addKeyValue("severity", event.severity())
                    .addKeyValue("ip", Optional.ofNullable(event.ipAddress()).orElse(UNKNOWN))
                    .log("Audit log persisted asynchronously");

        } catch (Exception ex) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("action", event.action())
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
        return new AuditLog()
                .setUserId(event.userId())
                .setAction(event.action())
                .setResourceType(event.resourceType())
                .setResourceId(event.resourceId())
                .setSeverity(event.severity())
                .setDetails(event.details())
                .setIpAddress(Optional.ofNullable(event.ipAddress()).orElse(UNKNOWN))
                .setUserAgent(Optional.ofNullable(event.userAgent()).orElse(UNKNOWN))
                .setTraceId(event.traceId());
    }
}
