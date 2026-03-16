package com.ahogek.cttserver.audit.repository;

import com.ahogek.cttserver.audit.entity.AuditLog;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link AuditLog} entity.
 *
 * <p>Provides standard CRUD operations and serves as the persistence interface for security audit
 * logs.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @see com.ahogek.cttserver.audit.entity.AuditLog
 * @since 2026-03-16
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
