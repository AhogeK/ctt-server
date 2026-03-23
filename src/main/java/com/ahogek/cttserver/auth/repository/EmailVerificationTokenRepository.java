package com.ahogek.cttserver.auth.repository;

import com.ahogek.cttserver.auth.entity.EmailVerificationToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
