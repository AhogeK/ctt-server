package com.ahogek.cttserver.mail.repository;

import com.ahogek.cttserver.mail.entity.MailOutbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for mail outbox operations.
 *
 * @author AhogeK
 * @since 2026-03-18
 */
@Repository
public interface MailOutboxRepository extends JpaRepository<MailOutbox, UUID> {

    /**
     * Finds dispatchable mail entries for the scheduler to process.
     *
     * <p>Returns new emails with status PENDING plus failed emails that have reached their retry
     * time and still have retries remaining.
     *
     * @param now the current timestamp for retry comparison
     * @param limit maximum number of records to fetch
     * @return list of dispatchable mail entries
     */
    @Query(
            """
        SELECT m FROM MailOutbox m
        WHERE m.status = 'PENDING'
           OR (m.status = 'FAILED' AND m.nextRetryAt <= :now AND m.retryCount < m.maxRetries)
        ORDER BY m.createdAt ASC
        """)
    List<MailOutbox> findDispatchable(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * Finds a mail entry by its OpenTelemetry trace ID.
     *
     * @param traceId the trace ID for distributed observability
     * @return optional containing the mail entry if found
     */
    Optional<MailOutbox> findByTraceId(String traceId);

    /**
     * Counts mail entries grouped by status for monitoring dashboards.
     *
     * @return list of status-count pairs
     */
    @Query("SELECT m.status, COUNT(m) FROM MailOutbox m GROUP BY m.status")
    List<Object[]> countByStatus();
}
