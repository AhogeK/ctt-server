package com.ahogek.cttserver.audit.repository;

import com.ahogek.cttserver.audit.entity.AuditLog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

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
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserId(UUID userId);
}
