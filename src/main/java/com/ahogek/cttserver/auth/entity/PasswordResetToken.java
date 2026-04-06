package com.ahogek.cttserver.auth.entity;

import com.ahogek.cttserver.auth.enums.TokenStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Password reset token entity (one-time use).
 *
 * <p>Represents a single-use token for password reset operations. State is derived dynamically from
 * timestamps rather than stored in a status column.
 *
 * <p>Security features:
 *
 * <ul>
 *   <li>SHA-256 hashed token storage (raw token never persisted)
 *   <li>One-hour expiration window
 *   <li>Automatic revocation of previous tokens on new request
 *   <li>Request metadata tracking (IP, User-Agent)
 *   <li>Optimistic locking via @Version (prevents concurrent consumption)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-06
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends AbstractToken {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Version
    @Column(name = "version")
    private Long version;

    public PasswordResetToken() {
        super();
    }

    /**
     * Determines current token status based on timestamps.
     *
     * <p>Status derivation logic (priority order):
     *
     * <ol>
     *   <li>UNAVAILABLE - if expiresAt is null (invalid state)
     *   <li>REVOKED - if revokedAt is set
     *   <li>CONSUMED - if consumedAt is set
     *   <li>EXPIRED - if current time exceeds expiresAt
     *   <li>VALID - otherwise
     * </ol>
     *
     * <p>Note: This method performs real-time timestamp comparison. In concurrent scenarios,
     * multiple threads may observe different statuses for the same token instance. The service
     * layer must implement appropriate locking or transaction isolation for critical operations.
     *
     * @return current token status
     */
    @Override
    public TokenStatus determineStatus() {
        if (this.expiresAt == null) {
            return TokenStatus.UNAVAILABLE;
        }

        if (this.revokedAt != null) {
            return TokenStatus.REVOKED;
        }

        if (this.consumedAt != null) {
            return TokenStatus.CONSUMED;
        }

        if (Instant.now().isAfter(this.expiresAt)) {
            return TokenStatus.EXPIRED;
        }

        return TokenStatus.VALID;
    }

    /**
     * Marks token as consumed (used for password reset).
     *
     * <p>Throws IllegalStateException if token is not in VALID status.
     */
    public void consume() {
        if (determineStatus() != TokenStatus.VALID) {
            throw new IllegalStateException(
                    "Token is not valid for consumption: " + determineStatus());
        }
        this.consumedAt = Instant.now();
    }

    /**
     * Marks token as revoked (manually invalidated).
     *
     * <p>Throws IllegalStateException if token is not in VALID status.
     */
    public void revoke() {
        if (determineStatus() != TokenStatus.VALID) {
            throw new IllegalStateException(
                    "Token is not valid for revocation: " + determineStatus());
        }
        this.revokedAt = Instant.now();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
