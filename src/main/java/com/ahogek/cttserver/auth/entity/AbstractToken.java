package com.ahogek.cttserver.auth.entity;

import com.ahogek.cttserver.auth.enums.TokenStatus;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base class for token entities.
 *
 * <p>Provides common fields and behavior shared by all token types:
 *
 * <ul>
 *   <li>Unique identifier
 *   <li>User association
 *   <li>Token hash for lookup
 *   <li>Expiration timestamp
 *   <li>Creation timestamp
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@MappedSuperclass
public abstract class AbstractToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;

    @Column(name = "user_id", nullable = false)
    protected UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    protected String tokenHash;

    @Column(name = "expires_at", nullable = false)
    protected Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    protected AbstractToken() {}

    /**
     * Determines current token status.
     *
     * <p>Subclasses must implement specific status derivation logic.
     *
     * @return current token status
     */
    public abstract TokenStatus determineStatus();

    /**
     * Checks if token is valid for use.
     *
     * @return true if token status is VALID
     */
    public boolean isValid() {
        return determineStatus().isValid();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
