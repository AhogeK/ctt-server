package com.ahogek.cttserver.audit.service;

import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.repository.AuditLogRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one audit row in a transaction of its own.
 *
 * <p>The caller runs outside any transaction, so a successful return means the row committed. That
 * separation is what makes the audit metric honest: counting inside a transactional method would
 * report a row as written even when the commit fails afterwards, and the failure would never reach
 * the dropped counter.
 *
 * <p>{@code REQUIRES_NEW} keeps an audit write from joining, and being rolled back with, the
 * business transaction that produced the event.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @see AuditLogService
 * @since 2026-11-11
 */
@Service
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    public AuditLogWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Persists the row and commits before returning.
     *
     * @param auditLog the row to store
     * @return the stored row
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog persist(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }
}
