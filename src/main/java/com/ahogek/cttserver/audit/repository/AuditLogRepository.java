package com.ahogek.cttserver.audit.repository;

import com.ahogek.cttserver.audit.entity.AuditLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Deletes one bounded batch of audit rows older than the cutoff.
     *
     * <p>Must run inside a transaction; {@code AuditRetentionJob} supplies one per batch.
     *
     * @param cutoff rows created before this instant are eligible
     * @param batchSize maximum rows to delete in this statement
     * @return number of deleted rows
     */
    @Modifying
    @Query(
            value =
                    "DELETE FROM audit_logs WHERE id IN"
                            + " (SELECT id FROM audit_logs WHERE created_at < :cutoff LIMIT :batchSize)",
            nativeQuery = true)
    int deleteBatchBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    List<AuditLog> findByUserId(UUID userId);
}
