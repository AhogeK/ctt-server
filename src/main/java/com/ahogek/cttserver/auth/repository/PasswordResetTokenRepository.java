package com.ahogek.cttserver.auth.repository;

import com.ahogek.cttserver.auth.entity.PasswordResetToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for password reset token persistence operations.
 *
 * <p>Provides CRUD operations and custom queries for password reset token management.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-06
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Finds a token by its SHA-256 hash.
     *
     * @param tokenHash the SHA-256 hash of the raw token
     * @return Optional containing the token if found
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Revokes all active (unexpired, unused, unrevoked) tokens for a user.
     *
     * <p>This bulk update is used when a new password reset request is made, ensuring only one
     * valid token exists per user at any time.
     *
     * <p>Conditions for revocation:
     *
     * <ul>
     *   <li>Token belongs to the specified user
     *   <li>Token has not been revoked (revokedAt IS NULL)
     *   <li>Token has not been consumed (consumedAt IS NULL)
     *   <li>Token has not expired (expiresAt > now)
     * </ul>
     *
     * @param userId the user ID whose tokens should be revoked
     * @param now the current timestamp for revocation
     * @return the number of tokens revoked
     */
    @Modifying
    @Query(
            "UPDATE PasswordResetToken t SET t.revokedAt = :now "
                    + "WHERE t.userId = :userId "
                    + "AND t.revokedAt IS NULL "
                    + "AND t.consumedAt IS NULL "
                    + "AND t.expiresAt > :now")
    int revokeActiveTokensByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
