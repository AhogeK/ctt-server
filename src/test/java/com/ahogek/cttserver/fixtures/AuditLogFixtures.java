package com.ahogek.cttserver.fixtures;

import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;

import java.util.UUID;

/**
 * Test fixtures for AuditLog entity. Provides Object Mother and Builder patterns for test data
 * creation.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Object Mother - preset states
 * var loginSuccess = AuditLogFixtures.loginSuccess().userId(userId).build();
 * var loginFailed = AuditLogFixtures.loginFailed().details(AuditDetails.reason("Invalid password")).build();
 * var securityAlert = AuditLogFixtures.securityAlert().resourceId("user-456").build();
 *
 * // Custom builder
 * var custom = AuditLogFixtures.builder()
 *         .action(AuditAction.API_KEY_REVOKED)
 *         .resourceType(ResourceType.API_KEY)
 *         .severity(SecuritySeverity.WARNING)
 *         .build();
 *
 * // Persisted (Repository test)
 * var persisted = PersistedFixtures.auditLog(em, AuditLogFixtures.loginSuccess().build());
 * }</pre>
 *
 * @author AhogeK
 * @since 2026-04-03
 */
public final class AuditLogFixtures {

    /** W3C Trace Context trace-id (32 lowercase hex chars) */
    public static final String DEFAULT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private AuditLogFixtures() {
        // Utility class - prevent instantiation
    }

    // ==========================================
    // Object Mother - Preset States
    // ==========================================

    /** A valid audit log with sensible defaults (INFO severity, LOGIN_SUCCESS action). */
    public static Builder validAuditLog() {
        return new Builder()
                .userId(UUID.randomUUID())
                .action(AuditAction.LOGIN_SUCCESS)
                .resourceType(ResourceType.USER)
                .resourceId("user-123")
                .severity(SecuritySeverity.INFO)
                .traceId(DEFAULT_TRACE_ID)
                .ipAddress("192.168.1.1")
                .userAgent("TestAgent/1.0")
                .details(AuditDetails.empty());
    }

    /** Login success scenario (INFO severity, USER resource type). */
    public static Builder loginSuccess() {
        return validAuditLog().action(AuditAction.LOGIN_SUCCESS);
    }

    /** Login failed scenario (WARNING severity, USER resource type). */
    public static Builder loginFailed() {
        return validAuditLog().action(AuditAction.LOGIN_FAILED).severity(SecuritySeverity.WARNING);
    }

    /** Logout success scenario (INFO severity, USER resource type). */
    public static Builder logoutSuccess() {
        return validAuditLog().action(AuditAction.LOGOUT_SUCCESS);
    }

    /** Security alert scenario (CRITICAL severity). */
    public static Builder securityAlert() {
        return validAuditLog()
                .action(AuditAction.SECURITY_ALERT)
                .severity(SecuritySeverity.CRITICAL);
    }

    /** Token refresh scenario (INFO severity, REFRESH_TOKEN resource type). */
    public static Builder tokenRefresh() {
        return validAuditLog()
                .action(AuditAction.REFRESH_TOKEN_ROTATED)
                .resourceType(ResourceType.REFRESH_TOKEN);
    }

    /** Token reuse detected scenario (CRITICAL severity, REFRESH_TOKEN resource type). */
    public static Builder tokenReuseDetected() {
        return validAuditLog()
                .action(AuditAction.REFRESH_TOKEN_REUSE_DETECTED)
                .resourceType(ResourceType.REFRESH_TOKEN)
                .severity(SecuritySeverity.CRITICAL);
    }

    /** API key created scenario (INFO severity, API_KEY resource type). */
    public static Builder apiKeyCreated() {
        return validAuditLog()
                .action(AuditAction.API_KEY_CREATED)
                .resourceType(ResourceType.API_KEY);
    }

    /** API key revoked scenario (WARNING severity, API_KEY resource type). */
    public static Builder apiKeyRevoked() {
        return validAuditLog()
                .action(AuditAction.API_KEY_REVOKED)
                .resourceType(ResourceType.API_KEY)
                .severity(SecuritySeverity.WARNING);
    }

    /** Rate limit exceeded scenario (WARNING severity, UNKNOWN resource type). */
    public static Builder rateLimitExceeded() {
        return validAuditLog()
                .action(AuditAction.RATE_LIMIT_EXCEEDED)
                .resourceType(ResourceType.UNKNOWN)
                .severity(SecuritySeverity.WARNING);
    }

    /** Email verification sent scenario (INFO severity, EMAIL_VERIFICATION resource type). */
    public static Builder emailVerificationSent() {
        return validAuditLog()
                .action(AuditAction.EMAIL_VERIFICATION_SENT)
                .resourceType(ResourceType.EMAIL_VERIFICATION);
    }

    /** Email verification success scenario (INFO severity, EMAIL_VERIFICATION resource type). */
    public static Builder emailVerificationSuccess() {
        return validAuditLog()
                .action(AuditAction.EMAIL_VERIFICATION_SUCCESS)
                .resourceType(ResourceType.EMAIL_VERIFICATION);
    }

    // ==========================================
    // Builder
    // ==========================================

    /** Creates a new builder for custom audit log construction. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for AuditLog entity. */
    public static final class Builder {
        private UUID userId;
        private AuditAction action = AuditAction.LOGIN_SUCCESS;
        private ResourceType resourceType = ResourceType.USER;
        private String resourceId = "user-123";
        private SecuritySeverity severity = SecuritySeverity.INFO;
        private AuditDetails details = AuditDetails.empty();
        private String ipAddress = "192.168.1.1";
        private String userAgent = "TestAgent/1.0";
        private String traceId = DEFAULT_TRACE_ID;

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
            this.details = details != null ? details : AuditDetails.empty();
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

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * Builds the AuditLog entity.
         *
         * <p>Note: createdAt is managed by JPA (@CreationTimestamp) and will be null until
         * persisted.
         *
         * @return new AuditLog instance
         */
        public AuditLog build() {
            var auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setSeverity(severity);
            auditLog.setDetails(details);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setTraceId(traceId);
            return auditLog;
        }
    }
}
