package com.ahogek.cttserver.auth.apikey.entity;

import com.ahogek.cttserver.auth.apikey.crypto.ApiKeyScopeConverter;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.user.entity.User;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * API key aggregate root representing an authentication credential issued to a user.
 *
 * <p>Maps to the {@code api_keys} table. The full key value is never stored — only its SHA-256 hash
 * and an 8-character {@code keyPrefix} that is safe to display for identification purposes. Scopes
 * are persisted as a JSONB array via {@link ApiKeyScopeConverter}.
 *
 * <p>State is encapsulated by the {@link #isActive()}, {@link #revoke(Instant)} and {@link
 * #touchLastUsed(Instant)} behavioral methods so callers cannot bypass lifecycle rules.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
@Entity
@Table(
        name = "api_keys",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_api_keys_key_prefix", columnNames = "key_prefix"),
            @UniqueConstraint(name = "uk_api_keys_key_hash", columnNames = "key_hash")
        })
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "name", length = 100)
    private String name;

    @Convert(converter = ApiKeyScopeConverter.class)
    @Column(name = "scopes", columnDefinition = "jsonb", nullable = false)
    private Set<ApiKeyScope> scopes = EnumSet.noneOf(ApiKeyScope.class);

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ApiKey() {}

    // ==========================================
    // Lifecycle behaviors
    // ==========================================

    /**
     * Returns whether the key is currently usable for authentication.
     *
     * <p>A key is active when it has not been revoked and has not yet expired. Calling this method
     * without an explicit {@code now} uses {@link Instant#now()}; the overload accepting an instant
     * is preferred from request-handling code so the value is reproducible.
     *
     * @return {@code true} if the key may be used right now
     */
    public boolean isActive() {
        return isActive(Instant.now());
    }

    /**
     * Returns whether the key is active at the supplied instant.
     *
     * @param now the reference instant; typically {@link Instant#now()}
     * @return {@code true} if neither revoked nor expired at {@code now}
     */
    public boolean isActive(Instant now) {
        if (revokedAt != null) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(now);
    }

    /**
     * Marks the key as revoked. Idempotent: revoking an already-revoked key is a no-op and
     * preserves the original {@code revokedAt} timestamp for audit fidelity.
     *
     * @param when the revocation instant; typically {@link Instant#now()}
     */
    public void revoke(Instant when) {
        if (revokedAt == null) {
            this.revokedAt = when;
        }
    }

    /**
     * Records that the key was just used for authentication.
     *
     * <p>Authentication code should invoke this from an asynchronous executor so that the write
     * latency does not extend the request critical path.
     *
     * @param when the usage instant; typically {@link Instant#now()}
     */
    public void touchLastUsed(Instant when) {
        this.lastUsedAt = when;
    }

    // ==========================================
    // Getters and Setters
    // ==========================================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<ApiKeyScope> getScopes() {
        return scopes;
    }

    public void setScopes(Set<ApiKeyScope> scopes) {
        this.scopes = scopes == null ? EnumSet.noneOf(ApiKeyScope.class) : scopes;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
