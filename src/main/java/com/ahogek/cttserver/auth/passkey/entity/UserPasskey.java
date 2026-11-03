package com.ahogek.cttserver.auth.passkey.entity;

import com.ahogek.cttserver.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A registered WebAuthn credential belonging to one account.
 *
 * <p>The credential ID and public key are public material; the private key stays on the
 * authenticator. Backup flags are display metadata, never an authorization gate.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Entity
@Table(
        name = "user_passkeys",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_user_passkeys_credential", columnNames = "credential_id")
        })
public class UserPasskey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "credential_id", nullable = false, length = 1364)
    private String credentialId;

    @Column(name = "public_key_cose", nullable = false, columnDefinition = "bytea")
    private byte[] publicKeyCose;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transports", columnDefinition = "jsonb", nullable = false)
    private List<String> transports = List.of();

    @Column(name = "aaguid")
    private UUID aaguid;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public UserPasskey() {}

    /**
     * Records a successful assertion and advances the stored authenticator state.
     *
     * @param newSignCount the signature counter returned by the authenticator
     * @param newBackupState the current BS flag
     * @param now the assertion instant
     */
    public void markUsed(long newSignCount, boolean newBackupState, Instant now) {
        this.signCount = newSignCount;
        this.backupState = newBackupState;
        this.lastUsedAt = now;
    }

    /**
     * Renames the credential as shown in the account settings.
     *
     * @param name the new human-readable label
     */
    public void rename(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public byte[] getPublicKeyCose() {
        return publicKeyCose;
    }

    public void setPublicKeyCose(byte[] publicKeyCose) {
        this.publicKeyCose = publicKeyCose;
    }

    public long getSignCount() {
        return signCount;
    }

    public void setSignCount(long signCount) {
        this.signCount = signCount;
    }

    public List<String> getTransports() {
        return transports;
    }

    public void setTransports(List<String> transports) {
        this.transports = transports == null ? List.of() : List.copyOf(transports);
    }

    public UUID getAaguid() {
        return aaguid;
    }

    public void setAaguid(UUID aaguid) {
        this.aaguid = aaguid;
    }

    public boolean isBackupEligible() {
        return backupEligible;
    }

    public void setBackupEligible(boolean backupEligible) {
        this.backupEligible = backupEligible;
    }

    public boolean isBackupState() {
        return backupState;
    }

    public void setBackupState(boolean backupState) {
        this.backupState = backupState;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
