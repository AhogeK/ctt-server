package com.ahogek.cttserver.auth.repository;

import com.ahogek.cttserver.auth.entity.EmailVerificationToken;

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
 * Repository for EmailVerificationToken entity.
 *
 * <p>EmailVerificationToken is an aggregate root with its own lifecycle. Tokens are one-time use
 * and state is derived dynamically from timestamps (consumedAt, revokedAt, expiresAt).
 *
 * <p>Query methods are designed to leverage underlying PostgreSQL indexes:
 *
 * <ul>
 *   <li>uk_email_verification_token_hash - unique index on token_hash
 *   <li>idx_email_verification_lookup - composite index on (user_id, purpose, expires_at,
 *       consumed_at, revoked_at)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-22
 */
@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    /**
     * Finds a token by its SHA-256 hash.
     *
     * <p>Uses unique index uk_email_verification_token_hash for O(log N) lookup.
     *
     * @param tokenHash the hex-encoded SHA-256 hash of the raw token
     * @return Optional containing the token if found
     */
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Finds the most recently created token for a user.
     *
     * @param userId the user ID
     * @return Optional containing the most recent token if any exist
     */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Retrieves all tokens for a specific user and purpose.
     *
     * <p>Used during resend verification flow to find and revoke previously issued tokens. Uses
     * composite index idx_email_verification_lookup (leftmost prefix: user_id, purpose).
     *
     * @param userId the user ID
     * @param purpose the token purpose (e.g., "REGISTER_VERIFY", "CHANGE_EMAIL")
     * @return list of tokens matching the criteria
     */
    List<EmailVerificationToken> findByUserIdAndPurpose(UUID userId, String purpose);

    /**
     * Checks if an unconsumed token exists for the given user and purpose.
     *
     * <p>Used for fast validation checks before issuing new tokens. Uses composite index
     * idx_email_verification_lookup. Returns true as soon as first match is found (short-circuit).
     *
     * @param userId the user ID
     * @param purpose the token purpose
     * @return true if an unconsumed token exists
     */
    boolean existsByUserIdAndPurposeAndConsumedAtIsNull(UUID userId, String purpose);

    /**
     * Finds all potentially valid tokens for a user.
     *
     * <p>Query returns tokens where consumedAt and revokedAt are null, and not yet expired. Used to
     * revoke all other tokens after successful verification or before resending.
     *
     * @param userId the user ID
     * @param now the current timestamp for expiration check
     * @return list of potentially valid tokens
     */
    @Query(
            """
            SELECT t FROM EmailVerificationToken t
            WHERE t.userId = :userId
              AND t.consumedAt IS NULL
              AND t.revokedAt IS NULL
              AND t.expiresAt > :now
            """)
    List<EmailVerificationToken> findValidTokensByUserId(UUID userId, Instant now);

    /**
     * Deletes all tokens for a user.
     *
     * <p>Used for cleanup when a user verifies their email or when account is deleted. This is a
     * bulk delete operation that bypasses the entity lifecycle.
     *
     * @param userId the user ID
     * @return number of tokens deleted
     */
    long deleteByUserId(UUID userId);

    /**
     * Finds the pending email-change token for a user, if any.
     *
     * <p>Matches tokens with purpose {@code CHANGE_EMAIL}, status {@code PENDING}, not revoked, and
     * not yet expired. Used by the email-change flow to detect and replace an existing pending
     * request before issuing a new one. Uses composite index idx_email_verification_lookup
     * (leftmost prefix: user_id, purpose).
     *
     * @param userId the user ID
     * @param now the current timestamp for expiration check
     * @return Optional containing the pending token if one exists
     */
    @Query(
            """
            SELECT t FROM EmailVerificationToken t
            WHERE t.userId = :userId
              AND t.purpose = 'CHANGE_EMAIL'
              AND t.status = 'PENDING'
              AND t.revokedAt IS NULL
              AND t.expiresAt > :now
            """)
    Optional<EmailVerificationToken> findPendingChangeEmailTokenByUserId(
            @Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Checks whether a pending email-change token exists for a user.
     *
     * <p>Fast existence check used to decide whether the email-change request can proceed (e.g., to
     * enforce a "one pending change at a time" invariant). Uses composite index
     * idx_email_verification_lookup.
     *
     * @param userId the user ID
     * @param now the current timestamp for expiration check
     * @return true if a pending, non-revoked, non-expired CHANGE_EMAIL token exists
     */
    @Query(
            """
            SELECT COUNT(t) > 0 FROM EmailVerificationToken t
            WHERE t.userId = :userId
              AND t.purpose = 'CHANGE_EMAIL'
              AND t.status = 'PENDING'
              AND t.revokedAt IS NULL
              AND t.expiresAt > :now
            """)
    boolean existsPendingChangeEmailTokenByUserId(
            @Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Cancels all pending email-change tokens for a user.
     *
     * <p>Sets status to {@code CANCELLED} and revokedAt to {@code now} for every matching token.
     * Used before issuing a new email-change token to ensure only one pending request is active at
     * a time. This is a bulk update that bypasses the entity lifecycle.
     *
     * @param userId the user ID
     * @param now the timestamp recorded as revokedAt
     * @return number of tokens cancelled
     */
    @Modifying
    @Query(
            """
            UPDATE EmailVerificationToken t
            SET t.status = 'CANCELLED', t.revokedAt = :now
            WHERE t.userId = :userId
              AND t.purpose = 'CHANGE_EMAIL'
              AND t.status = 'PENDING'
              AND t.revokedAt IS NULL
            """)
    int cancelPendingChangeEmailTokensByUserId(
            @Param("userId") UUID userId, @Param("now") Instant now);
}
