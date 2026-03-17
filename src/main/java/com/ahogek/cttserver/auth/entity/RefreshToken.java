package com.ahogek.cttserver.auth.entity;

import com.ahogek.cttserver.auth.enums.TokenStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Refresh token entity (long-lived).
 *
 * <p>Represents a long-lived token for session refresh. Supports explicit revocation for security
 * purposes.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AbstractToken {

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
