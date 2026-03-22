package com.ahogek.cttserver.auth.entity;

import com.ahogek.cttserver.auth.enums.TokenStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Email verification token entity (one-time use).
 *
 * <p>Represents a single-use token for email address verification. State is derived dynamically
 * from timestamps rather than stored in a status column.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends AbstractToken {

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public EmailVerificationToken() {
        super();
    }

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

    public void consume() {
        if (determineStatus() != TokenStatus.VALID) {
            throw new IllegalStateException(
                    "Token is not valid for consumption: " + determineStatus());
        }
        this.consumedAt = Instant.now();
    }

    public void revoke() {
        if (determineStatus() != TokenStatus.VALID) {
            throw new IllegalStateException(
                    "Token is not valid for revocation: " + determineStatus());
        }
        this.revokedAt = Instant.now();
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
}
