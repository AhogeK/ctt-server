package com.ahogek.cttserver.auth.repository;

import com.ahogek.cttserver.auth.entity.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RefreshToken entity.
 *
 * <p>RefreshToken is an aggregate root with its own lifecycle. Supports explicit revocation for
 * security purposes (password change, account lock, forced logout).
 *
 * <p>Query methods are designed to leverage underlying PostgreSQL indexes:
 *
 * <ul>
 *   <li>uk_refresh_tokens_token_hash - unique index on token_hash for O(log N) lookup
 *   <li>idx_refresh_tokens_user_id - index on user_id for user-scoped queries
 *   <li>idx_refresh_tokens_active - partial index on (user_id, expires_at) WHERE revoked_at IS NULL
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-23
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Finds a token by its SHA-256 hash.
     *
     * <p>Uses unique index uk_refresh_tokens_token_hash for O(log N) lookup.
     *
     * @param tokenHash the hex-encoded SHA-256 hash of the raw token
     * @return Optional containing the token if found
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Batch revokes all valid refresh tokens for a user.
     *
     * <p>Used for high-risk scenarios: password change, account lock, or forced logout of all
     * devices.
     *
     * <p>Uses partial index idx_refresh_tokens_active for optimized query.
     *
     * <p><strong>Note:</strong> Must be called within a @Transactional context.
     *
     * @param userId the user ID
     * @param now the current timestamp for revocation
     * @return number of tokens revoked
     */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken r
            SET r.revokedAt = :now
            WHERE r.userId = :userId
              AND r.revokedAt IS NULL
              AND r.expiresAt > :now
            """)
    int revokeAllUserTokens(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Revokes all tokens for a specific device.
     *
     * <p>Used when unlinking a device or when device-specific security events occur.
     *
     * <p><strong>Note:</strong> Must be called within a @Transactional context.
     *
     * @param userId the user ID
     * @param deviceId the device ID
     * @param now the current timestamp for revocation
     * @return number of tokens revoked
     */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken r
            SET r.revokedAt = :now
            WHERE r.userId = :userId
              AND r.deviceId = :deviceId
              AND r.revokedAt IS NULL
            """)
    int revokeDeviceTokens(
            @Param("userId") UUID userId,
            @Param("deviceId") UUID deviceId,
            @Param("now") Instant now);
}
