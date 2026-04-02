package com.ahogek.cttserver.auth.entity;

import com.ahogek.cttserver.auth.enums.TokenStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token entity (long-lived).
 *
 * <p>Represents a long-lived token for session refresh. Supports explicit revocation for security
 * purposes.
 *
 * <p>Database indexes:
 *
 * <ul>
 *   <li>uk_refresh_tokens_token_hash - unique index on token_hash for O(log N) lookup
 *   <li>idx_refresh_tokens_user_id - index on user_id for user-scoped queries
 *   <li>idx_refresh_tokens_active - partial index on (user_id, expires_at) WHERE revoked_at IS NULL
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AbstractToken {

    @Column(name = "issued_for", nullable = false)
    private String issuedFor = "WEB";

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public RefreshToken() {
        super();
    }

    /**
     * Determines current token status through dynamic derivation.
     *
     * <p>Checks revocation before expiration to prioritize security events.
     *
     * @return current token status
     */
    @Override
    public TokenStatus determineStatus() {
        // Defensive null check for unpersisted or incomplete entities
        if (this.expiresAt == null) {
            return TokenStatus.UNAVAILABLE;
        }

        if (this.revokedAt != null) {
            return TokenStatus.REVOKED;
        }

        if (Instant.now().isAfter(this.expiresAt)) {
            return TokenStatus.EXPIRED;
        }

        return TokenStatus.VALID;
    }

    /**
     * Revokes the token.
     *
     * <p>Marks token as revoked at current timestamp. Idempotent operation.
     */
    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
    }

    public String getIssuedFor() {
        return issuedFor;
    }

    public void setIssuedFor(String issuedFor) {
        this.issuedFor = issuedFor;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
