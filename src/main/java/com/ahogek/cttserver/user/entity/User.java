package com.ahogek.cttserver.user.entity;

import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.user.enums.UserStatus;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
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

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failure_time")
    private Instant lastFailureTime;

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
        this.emailVerifiedAt = Instant.now();
    }

    /**
     * Records a failed login attempt with sliding window logic.
     *
     * <p>Automatically locks the account if failed attempts reach the threshold.
     * The sliding window resets the counter if the last failure occurred outside the window.
     *
     * @param maxAttempts maximum allowed failed attempts before locking
     * @param lockDuration duration to lock the account when threshold is reached
     * @param windowSeconds sliding window duration in seconds (0 disables window logic)
     */
    public void recordFailedLogin(int maxAttempts, Duration lockDuration, int windowSeconds) {
        if (this.status == UserStatus.DELETED) {
            return;
        }

        if (this.failedLoginAttempts == null) {
            this.failedLoginAttempts = 0;
        }

        if (windowSeconds > 0 && this.lastFailureTime != null) {
            Instant windowStart = Instant.now().minusSeconds(windowSeconds);
            if (this.lastFailureTime.isBefore(windowStart)) {
                this.failedLoginAttempts = 0;
            }
        }

        this.failedLoginAttempts++;
        this.lastFailureTime = Instant.now();

        if (this.failedLoginAttempts >= maxAttempts && this.status == UserStatus.ACTIVE) {
            transitionTo(UserStatus.LOCKED);
            this.lockedUntil = Instant.now().plus(lockDuration);
        }
    }

    /**
     * Records a successful login.
     *
     * <p>Resets failed login counter, clears lock and failure time, and updates login timestamp.
     */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastFailureTime = null;
        this.lastLoginAt = Instant.now();

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

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastFailureTime() {
        return lastFailureTime;
    }
}
