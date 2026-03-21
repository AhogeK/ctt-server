package com.ahogek.cttserver.fixtures;

import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;

import java.util.UUID;

/**
 * Audit log test data factory using Object Mother and Builder patterns.
 *
 * <p>Provides preset audit events for common scenarios and a fluent builder for custom
 * construction.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Object Mother - preset events
 * var loginSuccess = AuditFixtures.loginSuccess(userId);
 * var loginFailed = AuditFixtures.loginFailed(userId, "Bad credentials");
 * var accountLocked = AuditFixtures.accountLocked(userId, 5);
 *
 * // Custom builder
 * var custom = AuditFixtures.builder()
 *         .action(AuditAction.RATE_LIMIT_EXCEEDED)
 *         .resourceType(ResourceType.USER)
 *         .severity(SecuritySeverity.WARNING)
 *         .build();
 *
 * // Persisted (Repository/Integration test)
 * var persisted = PersistedFixtures.auditLog(em, AuditFixtures.loginSuccess(userId));
 * }</pre>
 *
 * @author AhogeK
 * @since 2026-03-18
 */
public final class AuditFixtures {

    private AuditFixtures() {}

    // ==========================================
    // Object Mother - Preset Events
    // ==========================================

    /**
     * Creates a successful login audit event.
     *
     * @param userId user who logged in
     * @return builder for further customization
     */
    public static Builder loginSuccess(UUID userId) {
        return builder()
                .action(AuditAction.LOGIN_SUCCESS)
                .resourceType(ResourceType.USER)
                .resourceId(userId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates a failed login audit event.
     *
     * @param userId user who attempted login
     * @param reason failure reason
     * @return builder for further customization
     */
    public static Builder loginFailed(UUID userId, String reason) {
        return builder()
                .action(AuditAction.LOGIN_FAILED)
                .resourceType(ResourceType.USER)
                .resourceId(userId.toString())
                .userId(userId)
                .severity(SecuritySeverity.WARNING)
                .details(AuditDetails.reason(reason));
    }

    /**
     * Creates an account locked audit event.
     *
     * @param userId locked user
     * @param attemptCount number of failed attempts before lock
     * @return builder for further customization
     */
    public static Builder accountLocked(UUID userId, int attemptCount) {
        return builder()
                .action(AuditAction.ACCOUNT_LOCKED)
                .resourceType(ResourceType.USER)
                .resourceId(userId.toString())
                .userId(userId)
                .severity(SecuritySeverity.WARNING)
                .details(
                        AuditDetails.attempt(
                                attemptCount, "Account locked due to failed login attempts"));
    }

    /**
     * Creates a user registration request audit event.
     *
     * @param userId newly registered user
     * @return builder for further customization
     */
    public static Builder registerRequested(UUID userId) {
        return builder()
                .action(AuditAction.REGISTER_REQUESTED)
                .resourceType(ResourceType.USER)
                .resourceId(userId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates a user registration success audit event.
     *
     * @param userId newly registered user
     * @return builder for further customization
     */
    public static Builder registerSuccess(UUID userId) {
        return builder()
                .action(AuditAction.REGISTER_SUCCESS)
                .resourceType(ResourceType.USER)
                .resourceId(userId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates an email verification sent audit event.
     *
     * @param userId user to verify
     * @param tokenId verification token ID
     * @return builder for further customization
     */
    public static Builder emailVerificationSent(UUID userId, UUID tokenId) {
        return builder()
                .action(AuditAction.EMAIL_VERIFICATION_SENT)
                .resourceType(ResourceType.EMAIL_VERIFICATION)
                .resourceId(tokenId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates an email verification success audit event.
     *
     * @param userId verified user
     * @return builder for further customization
     */
    public static Builder emailVerificationSuccess(UUID userId) {
        return builder()
                .action(AuditAction.EMAIL_VERIFICATION_SUCCESS)
                .resourceType(ResourceType.USER)
                .resourceId(userId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates a password reset request audit event.
     *
     * @param userId user requesting reset
     * @param tokenId reset token ID
     * @return builder for further customization
     */
    public static Builder passwordResetRequested(UUID userId, UUID tokenId) {
        return builder()
                .action(AuditAction.PASSWORD_RESET_REQUESTED)
                .resourceType(ResourceType.PASSWORD_RESET)
                .resourceId(tokenId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates a password change audit event.
     *
     * @param userId user who changed password
     * @return builder for further customization
     */
    public static Builder passwordChanged(UUID userId) {
        return builder()
                .action(AuditAction.PASSWORD_CHANGED)
                .resourceType(ResourceType.USER)
                .resourceId(userId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates an API key created audit event.
     *
     * @param userId user who created the key
     * @param keyId API key ID
     * @return builder for further customization
     */
    public static Builder apiKeyCreated(UUID userId, UUID keyId) {
        return builder()
                .action(AuditAction.API_KEY_CREATED)
                .resourceType(ResourceType.API_KEY)
                .resourceId(keyId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates an API key revoked audit event.
     *
     * @param userId user who revoked the key
     * @param keyId API key ID
     * @return builder for further customization
     */
    public static Builder apiKeyRevoked(UUID userId, UUID keyId) {
        return builder()
                .action(AuditAction.API_KEY_REVOKED)
                .resourceType(ResourceType.API_KEY)
                .resourceId(keyId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates a rate limit exceeded audit event.
     *
     * @param ip client IP address
     * @param userAgent client user agent
     * @return builder for further customization
     */
    public static Builder rateLimitExceeded(String ip, String userAgent) {
        return builder()
                .action(AuditAction.RATE_LIMIT_EXCEEDED)
                .resourceType(ResourceType.UNKNOWN)
                .severity(SecuritySeverity.WARNING)
                .ipAddress(ip)
                .userAgent(userAgent)
                .details(AuditDetails.reason("Rate limit quota exceeded"));
    }

    /**
     * Creates a mail enqueued audit event.
     *
     * @param userId user who triggered the email
     * @param outboxId mail outbox entry ID
     * @return builder for further customization
     */
    public static Builder mailEnqueued(UUID userId, UUID outboxId) {
        return builder()
                .action(AuditAction.MAIL_ENQUEUED)
                .resourceType(ResourceType.MAIL_OUTBOX)
                .resourceId(outboxId.toString())
                .userId(userId)
                .severity(SecuritySeverity.INFO);
    }

    /**
     * Creates an unauthorized access audit event.
     *
     * @param ip client IP address
     * @param userAgent client user agent
     * @return builder for further customization
     */
    public static Builder unauthorizedAccess(String ip, String userAgent) {
        return builder()
                .action(AuditAction.UNAUTHORIZED_ACCESS)
                .resourceType(ResourceType.UNKNOWN)
                .severity(SecuritySeverity.WARNING)
                .ipAddress(ip)
                .userAgent(userAgent)
                .details(AuditDetails.reason("Missing or invalid authentication"));
    }

    // ==========================================
    // Builder
    // ==========================================

    /**
     * Creates a new builder for custom audit log construction.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for AuditLog entity. */
    public static final class Builder {
        private UUID userId;
        private AuditAction action = AuditAction.LOGIN_SUCCESS;
        private ResourceType resourceType = ResourceType.USER;
        private String resourceId;
        private SecuritySeverity severity = SecuritySeverity.INFO;
        private AuditDetails details = AuditDetails.empty();
        private String ipAddress;
        private String userAgent;

        private Builder() {}

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder action(AuditAction action) {
            this.action = action;
            return this;
        }

        public Builder resourceType(ResourceType resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder severity(SecuritySeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder details(AuditDetails details) {
            this.details = details;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Builds the AuditLog entity.
         *
         * <p>Note: createdAt is managed by JPA (@CreationTimestamp) and will be null until
         * persisted.
         *
         * <p>Uses fluent setter chain (returns this for each setter).
         *
         * @return new AuditLog instance
         */
        public AuditLog build() {
            var log = new AuditLog();
            log.setUserId(userId);
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setSeverity(severity);
            log.setDetails(details);
            log.setIpAddress(ipAddress);
            log.setUserAgent(userAgent);
            return log;
        }
    }
}
