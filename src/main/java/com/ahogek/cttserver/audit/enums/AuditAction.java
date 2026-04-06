package com.ahogek.cttserver.audit.enums;

/**
 * Standardized audit actions following bipartite naming grammar.
 *
 * <p>Naming convention: [DOMAIN]_[LIFECYCLE_STATE]
 *
 * <p>Examples: LOGIN_FAILED, API_KEY_REVOKED, EMAIL_VERIFICATION_SENT
 *
 * <p>This structure enables:
 *
 * <ul>
 *   <li>Prefix-based filtering for domain-specific queries
 *   <li>Suffix-based aggregation for lifecycle state analysis
 *   <li>State machine reconstruction from audit logs
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public enum AuditAction {

    // ==========================================
    // Identity and Access Management (IAM)
    // ==========================================

    /** User registration requested. */
    REGISTER_REQUESTED("User registration requested"),

    /** User registration completed successfully. */
    REGISTER_SUCCESS("User registration completed"),

    /** Successful authentication. */
    LOGIN_SUCCESS("Successful login"),

    /** Failed authentication attempt (bad credentials, locked account, etc.). */
    LOGIN_FAILED("Failed login attempt"),

    /** User logged out successfully. */
    LOGOUT_SUCCESS("User logged out successfully"),

    /** User logged out from all devices successfully (Kill Switch). */
    LOGOUT_ALL_DEVICES("User logged out from all devices successfully"),

    /** Security alert triggered (e.g., unauthorized access attempt). */
    SECURITY_ALERT("Security alert triggered"),

    /** Account automatically locked due to security violations. */
    ACCOUNT_LOCKED("Account automatically locked due to violations"),

    // ==========================================
    // Email Verification Mechanism
    // ==========================================

    /** Verification email dispatched to outbox. */
    EMAIL_VERIFICATION_SENT("Verification email dispatched to outbox"),

    /** Email address verified successfully. */
    EMAIL_VERIFICATION_SUCCESS("Email address verified successfully"),

    /** Email verification failed (invalid or expired token). */
    EMAIL_VERIFICATION_FAILED("Email verification failed"),

    // ==========================================
    // Credential Management
    // ==========================================

    /** Password reset requested for non-existent or inactive email (anti-enumeration). */
    PASSWORD_RESET_EMAIL_NOT_FOUND("Password reset requested for non-existent or inactive email"),

    /** Password reset token generated and dispatched. */
    PASSWORD_RESET_REQUESTED("Password reset token generated and requested"),

    /** Password successfully reset via valid token. */
    PASSWORD_RESET_SUCCESS("Password successfully reset via token"),

    /** Password updated via user profile settings. */
    PASSWORD_CHANGED("Password updated via user profile"),

    /** New API key generated for device or service. */
    API_KEY_CREATED("New API key generated"),

    /** API key revoked and invalidated. */
    API_KEY_REVOKED("API key revoked"),

    // ==========================================
    // Device and Endpoint Management
    // ==========================================

    /** New client device linked to user account. */
    DEVICE_LINKED("New client device linked to account"),

    /** Device forcefully unlinked from account. */
    DEVICE_UNLINKED("Device forcefully unlinked"),

    /** Refresh token rotated during refresh flow. */
    REFRESH_TOKEN_ROTATED("Refresh token rotated during refresh flow"),

    /** Refresh token reuse detected - potential token theft attack. */
    REFRESH_TOKEN_REUSE_DETECTED("Refresh token reuse detected - potential token theft attack"),

    // ==========================================
    // Security and Defense
    // ==========================================

    /** Rate limit quota exceeded. */
    RATE_LIMIT_EXCEEDED("Rate limit quota exceeded"),

    /** Mail operation skipped due to idempotency protection. */
    MAIL_IDEMPOTENT_SKIP("Mail operation skipped due to idempotency"),

    /** Unauthorized access attempt (401 - missing or invalid token). */
    UNAUTHORIZED_ACCESS("Unauthorized access attempt"),

    /** Forbidden resource access (403 - insufficient permissions). */
    FORBIDDEN_ACCESS("Forbidden resource access attempt"),

    /** Potential malicious payload intercepted. */
    MALICIOUS_PAYLOAD_DETECTED("Potential malicious payload intercepted"),

    // ==========================================
    // Mail Delivery
    // ==========================================

    /** Email queued to outbox for delivery. */
    MAIL_ENQUEUED("Email queued to outbox for delivery"),

    /** Email delivered successfully. */
    MAIL_SENT("Email delivered successfully"),

    /** Email delivery failed, scheduled for retry. */
    MAIL_DELIVERY_FAILED("Email delivery failed, scheduled for retry"),

    /** Email delivery exhausted max retries. */
    MAIL_DELIVERY_EXHAUSTED("Email delivery exhausted max retries");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
