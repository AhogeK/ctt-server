package com.ahogek.cttserver.auth.repository;

import com.ahogek.cttserver.auth.entity.EmailVerificationToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EmailVerificationToken entity.
 *
 * <p>EmailVerificationToken is an aggregate root with its own lifecycle. Tokens are one-time use
 * and state is derived dynamically from timestamps (consumedAt, revokedAt, expiresAt).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-22
 */
@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    /**
     * Finds a token by its hash value.
     *
     * <p>Used during email verification to look up the token from the verification link. The
     * token_hash column has a unique index for O(log N) lookup.
     *
     * @param tokenHash the SHA-256 hash of the raw token
     * @return Optional containing the token if found
     */
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Finds the most recently created token for a user.
     *
     * <p>Used in the resend verification flow to check if a recent valid token exists before
     * creating a new one. Returns the latest token regardless of status (valid, expired, consumed,
     * revoked).
     *
     * @param userId the user ID
     * @return Optional containing the most recent token if any exist
     */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

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
    @org.springframework.data.jpa.repository.Query(
            "SELECT t FROM EmailVerificationToken t WHERE t.userId = :userId "
                    + "AND t.consumedAt IS NULL AND t.revokedAt IS NULL AND t.expiresAt > :now")
    java.util.List<EmailVerificationToken> findValidTokensByUserId(
            UUID userId, java.time.Instant now);

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
