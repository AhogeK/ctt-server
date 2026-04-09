package com.ahogek.cttserver.auth.repository;

import com.ahogek.cttserver.auth.entity.LoginAttempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for {@link LoginAttempt} entity.
 *
 * <p>LoginAttempt is an append-only record used for brute-force detection. This repository provides
 * query methods designed to leverage the following PostgreSQL indexes:
 *
 * <ul>
 *   <li>{@code idx_login_attempts_email_hash} — single-column index for email-scoped lookups
 *   <li>{@code idx_login_attempts_email_attempt_at} — composite index for windowed attempt counts
 *   <li>{@code idx_login_attempts_attempt_at} — time-based index for retention cleanup
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-09
 */
@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /**
     * Counts login attempts for an email within the sliding window.
     *
     * <p>Uses composite index {@code idx_login_attempts_email_attempt_at} for efficient range scan
     * on {@code (email_hash, attempt_at)}.
     *
     * @param emailHash SHA-256 hex digest of the lowercase email address
     * @param windowStart the start of the sliding window (exclusive lower bound)
     * @return number of attempts within the window
     */
    @Query(
            """
        SELECT COUNT(a) FROM LoginAttempt a
        WHERE a.emailHash = :emailHash
          AND a.attemptAt > :windowStart
        """)
    long countAttemptsInWindow(
            @Param("emailHash") String emailHash, @Param("windowStart") Instant windowStart);

    /**
     * Returns the timestamp of the earliest attempt in the window.
     *
     * <p>Used to determine if the lockout period has expired by comparing the first attempt time
     * against the lockout duration.
     *
     * <p>Uses composite index {@code idx_login_attempts_email_attempt_at}.
     *
     * @param emailHash SHA-256 hex digest of the lowercase email address
     * @param windowStart the start of the sliding window (exclusive lower bound)
     * @return the earliest attempt timestamp, or {@link Optional#empty()} if no attempts exist
     */
    @Query(
            """
        SELECT MIN(a.attemptAt) FROM LoginAttempt a
        WHERE a.emailHash = :emailHash
          AND a.attemptAt > :windowStart
        """)
    Optional<Instant> findEarliestAttemptInWindow(
            @Param("emailHash") String emailHash, @Param("windowStart") Instant windowStart);

    /**
     * Deletes all attempts older than the retention period.
     *
     * <p>Uses index {@code idx_login_attempts_attempt_at} for efficient time-based deletion. Should
     * be called periodically by a scheduled cleanup task.
     *
     * <p><strong>Note:</strong> Must be called within a {@code @Transactional} context.
     *
     * @param cutoff attempts before this timestamp will be deleted
     * @return number of deleted rows
     */
    @Modifying
    @Query(
            """
        DELETE FROM LoginAttempt a
        WHERE a.attemptAt < :cutoff
        """)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    /**
     * Deletes all attempts for a specific email.
     *
     * <p>Called on successful login to reset the brute-force counter.
     *
     * <p><strong>Note:</strong> Must be called within a {@code @Transactional} context.
     *
     * @param emailHash SHA-256 hex digest of the lowercase email address
     * @return number of deleted rows
     */
    @Modifying
    @Query(
            """
        DELETE FROM LoginAttempt a
        WHERE a.emailHash = :emailHash
        """)
    int deleteByEmailHash(@Param("emailHash") String emailHash);
}
