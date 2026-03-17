package com.ahogek.cttserver.user.entity;

import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.user.enums.UserStatus;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity representing a registered end user.
 *
 * <p>This is an aggregate root that encapsulates user state machine transitions and domain rules.
 * All state changes must go through behavioral methods to ensure state machine integrity.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {}

    // ==========================================
    // Lifecycle Hooks
    // ==========================================

    /**
     * Normalizes email before persistence operations.
     *
     * <p>Final defense for data integrity, handling cases where entities are created bypassing DTO
     * validation (admin scripts, data imports, etc.).
     */
    @PrePersist
    @PreUpdate
    protected void normalizeEmail() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase();
        }
    }

    // ==========================================
    // State Machine Transition Behaviors
    // ==========================================

    /**
     * Verifies user email and transitions to ACTIVE state.
     *
     * @throws ConflictException if state transition is not allowed
     */
    public void verifyEmail() {
        transitionTo(UserStatus.ACTIVE);
        this.emailVerified = true;
    }

    /**
     * Records a failed login attempt.
     *
     * <p>Automatically locks the account if failed attempts reach the threshold.
     *
     * @param maxAttempts maximum allowed failed attempts before locking
     */
    public void recordFailedLogin(int maxAttempts) {
        if (this.status == UserStatus.DELETED) {
            return;
        }

        // Defensive null check for database compatibility
        if (this.failedLoginAttempts == null) {
            this.failedLoginAttempts = 0;
        }

        this.failedLoginAttempts++;

        if (this.failedLoginAttempts >= maxAttempts && this.status == UserStatus.ACTIVE) {
            transitionTo(UserStatus.LOCKED);
        }
    }

    /**
     * Records a successful login.
     *
     * <p>Resets failed login counter and unlocks account if applicable.
     */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;

        if (this.status == UserStatus.LOCKED) {
            transitionTo(UserStatus.ACTIVE);
        }
    }

    /**
     * Suspends user account due to violations.
     *
     * @throws ConflictException if state transition is not allowed
     */
    public void suspend() {
        transitionTo(UserStatus.SUSPENDED);
    }

    /**
     * Reactivates a suspended user account.
     *
     * @throws ConflictException if state transition is not allowed
     */
    public void reactivate() {
        transitionTo(UserStatus.ACTIVE);
    }

    /**
     * Soft deletes the user account.
     *
     * <p>Data anonymization is performed according to GDPR requirements.
     *
     * @throws ConflictException if state transition is not allowed
     */
    public void markAsDeleted() {
        transitionTo(UserStatus.DELETED);

        // Data anonymization (GDPR compliance)
        // Defensive null check for unpersisted entities
        String idStr = this.id != null ? this.id.toString() : UUID.randomUUID().toString();
        this.email = idStr + "@deleted.local";
        this.displayName = "Deleted User";
        this.passwordHash = null;
        this.emailVerified = false;
    }

    // ==========================================
    // Core Transition Guard
    // ==========================================

    /**
     * Validates and performs state transition.
     *
     * @param nextStatus the target state
     * @throws ConflictException if transition is not allowed
     */
    private void transitionTo(UserStatus nextStatus) {
        if (!this.status.canTransitionTo(nextStatus)) {
            throw new ConflictException(
                    ErrorCode.COMMON_003,
                    String.format(
                            "Invalid state transition from %s to %s", this.status, nextStatus));
        }
        this.status = nextStatus;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Integer getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(Integer failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
