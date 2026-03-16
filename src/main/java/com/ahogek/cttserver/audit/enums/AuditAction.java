package com.ahogek.cttserver.audit.enums;

/**
 * Audit actions covering the full lifecycle from normal business flow to malicious attack
 * interception.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public enum AuditAction {

    // Authentication and Identity
    USER_REGISTERED("User registration"),
    LOGIN_SUCCESS("Successful login"),
    LOGIN_FAILED("Failed login attempt"),
    LOGOUT("User logout"),
    PASSWORD_CHANGED("Password updated"),
    PASSWORD_RESET_REQUESTED("Password reset requested"),
    EMAIL_VERIFIED("Email address verified"),
    ACCOUNT_LOCKED("Account locked due to violations"),

    // Credentials and Devices
    API_KEY_CREATED("API Key generated"),
    API_KEY_REVOKED("API Key revoked"),
    DEVICE_REGISTERED("New device linked"),
    DEVICE_UNLINKED("Device unlinked"),

    // Domain Operations
    SYNC_PUSH_COMPLETED("Sync push completed"),
    SYNC_CONFLICT_DETECTED("Sync conflict detected"),

    // Security and Mitigation
    SECURITY_VIOLATION("General security violation detected"),
    RATE_LIMIT_EXCEEDED("Rate limit quota exceeded"),
    UNAUTHORIZED_ACCESS("Unauthorized access attempt"),
    FORBIDDEN_ACCESS("Forbidden resource access attempt");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
