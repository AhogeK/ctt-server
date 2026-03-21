package com.ahogek.cttserver.mail.repository;

import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link MailOutbox} persistence operations.
 *
 * <p>Query strategy notes:
 *
 * <ul>
 *   <li>{@code findPendingJobs} — scheduler polling; relies on {@code idx_mail_outbox_dispatch}
 *       composite index {@code (status, next_retry_at, created_at)}.
 *   <li>{@code countDuplicates} — rate-guard before enqueue; intentionally counts only
 *       terminal-safe statuses to avoid false positives from in-flight {@code SENDING} records.
 * </ul>
 *
 * @author AhogeK
 * @since 2026-03-18
 */
@Repository
public interface MailOutboxRepository extends JpaRepository<MailOutbox, UUID> {

    /**
     * Returns dispatchable records for the scheduler in a single paginated batch.
     *
     * <p>Covers two cases:
     *
     * <ol>
     *   <li>Fresh emails: {@code status = PENDING} with no {@code nextRetryAt} constraint
     *       (first-time sends are always eligible).
     *   <li>Scheduled retries: {@code status = FAILED}, retry window elapsed, retries not yet
     *       exhausted.
     * </ol>
     *
     * <p>Use {@link Pageable} to control batch size, e.g. {@code PageRequest.of(0, 50,
     * Sort.by("createdAt"))}. The {@code ORDER BY} inside the query is intentionally omitted — let
     * the caller's {@code Pageable} sort drive it to stay composable.
     *
     * @param now current timestamp; FAILED records with {@code nextRetryAt > now} are excluded
     * @param pageable batch size and sort; typically {@code PageRequest.of(0, N,
     *     Sort.by("createdAt").ascending())}
     * @return ordered list of records ready for dispatch, never {@code null}
     */
    @Query(
            """
            SELECT m FROM MailOutbox m
            WHERE m.status = com.ahogek.cttserver.mail.enums.MailOutboxStatus.PENDING
               OR (    m.status = com.ahogek.cttserver.mail.enums.MailOutboxStatus.FAILED
                   AND m.nextRetryAt <= :now
                   AND m.retryCount < m.maxRetries)
            """)
    List<MailOutbox> findPendingJobs(@Param("now") Instant now, Pageable pageable);

    /**
     * Looks up a delivery record by its OpenTelemetry trace ID.
     *
     * <p>Useful for correlating a distributed trace span back to the outbox entry without knowing
     * the primary key.
     *
     * @param traceId the 32-hex-char W3C trace ID captured at enqueue time
     * @return the matching entry, or {@link Optional#empty()} if not found
     */
    Optional<MailOutbox> findByTraceId(String traceId);

    /**
     * Counts recent outbox entries for a given recipient, business type, and status set within a
     * rolling time window.
     *
     * <p>Used as a <b>rate-guard</b> before enqueueing a new email: if this count exceeds a
     * configured threshold the caller should reject or deduplicate the request.
     *
     * <p>Example — prevent sending more than 3 verification emails within 10 minutes:
     *
     * <pre>{@code
     * long recent = repo.countDuplicates(
     *     recipient, "REGISTER_VERIFY",
     *     List.of(MailOutboxStatus.PENDING, MailOutboxStatus.SENDING, MailOutboxStatus.SENT),
     *     Instant.now().minus(10, ChronoUnit.MINUTES)
     * );
     * if (recent >= 3) throw new TooManyRequestsException(...);
     * }</pre>
     *
     * @param recipient target email address
     * @param bizType business category, e.g. {@code "REGISTER_VERIFY"}
     * @param statuses statuses to include; typically {@code [PENDING, SENDING, SENT]} to catch all
     *     non-cancelled attempts
     * @param windowStart the start of the rolling time window (exclusive lower bound)
     * @return count of matching entries in the window
     */
    @Query(
            """
            SELECT COUNT(m) FROM MailOutbox m
            WHERE m.recipient   = :recipient
              AND m.bizType     = :bizType
              AND m.status      IN :statuses
              AND m.createdAt   > :windowStart
            """)
    long countDuplicates(
            @Param("recipient") String recipient,
            @Param("bizType") String bizType,
            @Param("statuses") List<MailOutboxStatus> statuses,
            @Param("windowStart") Instant windowStart);

    /**
     * Aggregates delivery counts grouped by status for monitoring dashboards.
     *
     * <p>Prefer exposing this via a dedicated metrics endpoint rather than polling in hot paths —
     * the full-table aggregation is expensive at scale.
     *
     * @return list of {@code [MailOutboxStatus, Long]} pairs
     */
    @Query("SELECT m.status, COUNT(m) FROM MailOutbox m GROUP BY m.status")
    List<Object[]> countByStatus();

    /**
     * Checks if a mail entry exists for the given recipient, business type, business ID, and status
     * set within a time window.
     *
     * <p>Used for idempotency protection to prevent duplicate email enqueues within a time window.
     *
     * @param recipient target email address
     * @param bizType business category, e.g. {@code "REGISTER_VERIFY"}
     * @param bizId the business entity ID (typically userId)
     * @param statuses statuses to include; typically {@code [PENDING, SENDING, SENT]}
     * @param windowStart the start of the time window (exclusive lower bound)
     * @return {@code true} if a matching entry exists
     */
    boolean existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
            String recipient,
            String bizType,
            UUID bizId,
            List<MailOutboxStatus> statuses,
            Instant windowStart);

    /**
     * Resets stuck SENDING records back to PENDING for zombie recovery.
     *
     * <p>Used when a Pod crashes while processing, leaving records in SENDING state indefinitely.
     * This bulk update avoids loading records into memory.
     *
     * @param timeoutThreshold records with updatedAt before this threshold are considered stuck
     * @param now current timestamp to set as new updatedAt
     * @return number of records reset to PENDING
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE MailOutbox m
            SET m.status = com.ahogek.cttserver.mail.enums.MailOutboxStatus.PENDING,
                m.updatedAt = :now
            WHERE m.status = com.ahogek.cttserver.mail.enums.MailOutboxStatus.SENDING
              AND m.updatedAt < :timeoutThreshold
            """)
    int resetStuckSendingJobs(
            @Param("timeoutThreshold") Instant timeoutThreshold, @Param("now") Instant now);
}
