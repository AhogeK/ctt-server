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

    public EmailVerificationToken() {
        super();
    }

    /**
     * Determines current token status through dynamic derivation.
     *
     * <p>Derives state from timestamps in real-time, eliminating the need for scheduled status
     * updates.
     *
     * @return current token status
     */
    @Override
    public TokenStatus determineStatus() {
        // Defensive null check for unpersisted or incomplete entities
        if (this.expiresAt == null) {
            return TokenStatus.UNAVAILABLE;
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
     * Consumes the token.
     *
     * <p>Marks token as consumed at current timestamp. Validates token status before consumption.
     *
     * @throws IllegalStateException if token is not in VALID status
     */
    public void consume() {
        if (determineStatus() != TokenStatus.VALID) {
            throw new IllegalStateException(
                    "Token is not valid for consumption: " + determineStatus());
        }
        this.consumedAt = Instant.now();
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
