package com.ahogek.cttserver.auth.repository;

import com.ahogek.cttserver.auth.entity.RefreshToken;

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
     * Finds the live token of one session.
     *
     * <p>A session is a chain of rows — rotation revokes the previous row and inserts the next — so
     * the live row carries the attributes that belong to the session rather than to a single token:
     * audience, device binding, TTL and client User-Agent.
     *
     * @param userId the owning user
     * @param sessionId the session identity
     * @return the live row when the session still has one
     */
    Optional<RefreshToken> findFirstByUserIdAndSessionIdAndRevokedAtIsNullOrderByCreatedAtDesc(
            UUID userId, UUID sessionId);

    /**
     * One active session as the session list reports it.
     *
     * <p>Carries the live row's own attributes (audience, device binding, User-Agent) together with
     * the aggregates that describe the whole session chain.
     */
    interface SessionRow {

        UUID getSessionId();

        String getIssuedFor();

        UUID getDeviceId();

        String getUserAgent();

        Instant getCreatedAt();

        Instant getLastUsedAt();

        Instant getExpiresAt();
    }

    /**
     * Lists one active row per session, carrying the session-wide first-issued / last-used /
     * last-expiring timestamps.
     *
     * <p>Two questions are answered separately on purpose. Which sessions to report and what to say
     * about them is decided by {@code live_session}: a session appears when it still has a row that
     * is neither revoked nor expired, and the reported audience, device binding and User-Agent come
     * from that newest live row. How long the session has been running and when it last refreshed
     * is answered by {@code session_span}, which spans <em>every</em> row of the session — the
     * previous token of a rotation is revoked, so aggregating only live rows would report a
     * refreshed session as never used.
     *
     * <p>Alias quoting matters: PostgreSQL folds unquoted aliases to lower case, which would stop
     * them matching the projection's getters.
     *
     * @param userId the owning user
     * @param now the instant sessions must still be live at
     * @return one row per active session
     */
    @Query(
            value =
                    """
                    WITH session_span AS (
                        SELECT session_id,
                               MIN(created_at)  AS first_issued,
                               MAX(last_used_at) AS last_used,
                               MAX(expires_at)  AS last_expires
                        FROM refresh_tokens
                        WHERE user_id = :userId
                        GROUP BY session_id
                    ),
                    live_session AS (
                        SELECT DISTINCT ON (session_id)
                               session_id, issued_for, device_id, user_agent
                        FROM refresh_tokens
                        WHERE user_id = :userId
                          AND revoked_at IS NULL
                          AND expires_at > :now
                        ORDER BY session_id, created_at DESC
                    )
                    SELECT l.session_id AS "sessionId",
                           l.issued_for AS "issuedFor",
                           l.device_id AS "deviceId",
                           l.user_agent AS "userAgent",
                           s.first_issued AS "createdAt",
                           s.last_used AS "lastUsedAt",
                           s.last_expires AS "expiresAt"
                    FROM live_session l
                    JOIN session_span s ON s.session_id = l.session_id
                    """,
            nativeQuery = true)
    List<SessionRow> findActiveSessionRows(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Takes the session-scoped exclusive lock that serializes rotation against revocation.
     *
     * <p>Without it a refresh that starts while a session is being revoked can insert the next
     * token after the revoking update has already looked at the session's rows — and the "kicked
     * out" session comes back. Both writers take this lock first, so the two orders are equivalent.
     * The lock is transaction-scoped and released by commit or rollback.
     *
     * @param sessionId the session to lock
     */
    @Query(
            value = "SELECT pg_advisory_xact_lock(hashtext(CAST(:sessionId AS text)))",
            nativeQuery = true)
    void lockSession(@Param("sessionId") UUID sessionId);

    /**
     * Revokes every live token of one session.
     *
     * @param userId the owning user (scoping the update keeps a foreign session indistinguishable
     *     from an unknown one)
     * @param sessionId the session to revoke
     * @param now the revocation instant
     * @return the number of revoked rows
     */
    @Modifying
    @Query(
            """
            UPDATE RefreshToken r
            SET r.revokedAt = :now
            WHERE r.userId = :userId AND r.sessionId = :sessionId AND r.revokedAt IS NULL
            """)
    int revokeSession(
            @Param("userId") UUID userId,
            @Param("sessionId") UUID sessionId,
            @Param("now") Instant now);

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
