package com.ahogek.cttserver.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Unified error code system for the application.
 *
 * <p>Error codes are organized by domain groups: COMMON, AUTH, USER, MAIL, RATE_LIMIT, SECURITY,
 * SYSTEM
 *
 * <p>Format: {@code GROUP_CODE} (e.g., COMMON_001, AUTH_001)
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public enum ErrorCode {

    // =========================================================================
    // COMMON - Common errors applicable across the system
    // =========================================================================
    COMMON_001("Invalid request parameters", HttpStatus.BAD_REQUEST),
    COMMON_002("Resource not found", HttpStatus.NOT_FOUND),
    COMMON_003("Validation error", HttpStatus.BAD_REQUEST),
    COMMON_004("Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    COMMON_005("Missing required field", HttpStatus.BAD_REQUEST),
    COMMON_006("Invalid field format", HttpStatus.BAD_REQUEST),

    // =========================================================================
    // AUTH - Authentication and authorization errors
    // =========================================================================
    AUTH_001("Invalid credentials", HttpStatus.UNAUTHORIZED),
    AUTH_002("Token expired", HttpStatus.UNAUTHORIZED),
    AUTH_003("Token invalid", HttpStatus.UNAUTHORIZED),
    AUTH_004("Account locked", HttpStatus.FORBIDDEN),
    AUTH_005("Account suspended", HttpStatus.FORBIDDEN),
    AUTH_006("Email not verified", HttpStatus.FORBIDDEN),
    AUTH_007("Refresh token expired", HttpStatus.UNAUTHORIZED),
    AUTH_008("Refresh token revoked", HttpStatus.UNAUTHORIZED),
    AUTH_009("Refresh token reuse detected", HttpStatus.FORBIDDEN),
    AUTH_010("API key invalid", HttpStatus.UNAUTHORIZED),
    AUTH_011("API key expired", HttpStatus.UNAUTHORIZED),
    AUTH_012("API key revoked", HttpStatus.FORBIDDEN),
    AUTH_013("OAuth state validation failed", HttpStatus.FORBIDDEN),
    AUTH_014("OAuth token decryption failed", HttpStatus.UNAUTHORIZED),
    AUTH_015("OAuth provider error", HttpStatus.BAD_GATEWAY),
    AUTH_016("OAuth account already linked", HttpStatus.CONFLICT),
    AUTH_017("OAuth account not linked", HttpStatus.BAD_REQUEST),
    AUTH_018("Cannot unlink last credential", HttpStatus.BAD_REQUEST),
    PASSWORD_SAME_AS_OLD(
            "New password cannot be the same as the current password", HttpStatus.CONFLICT),

    // =========================================================================
    // USER - User management errors
    // =========================================================================
    USER_001("Email already registered", HttpStatus.CONFLICT),
    USER_002("Invalid email format", HttpStatus.BAD_REQUEST),
    USER_003("Password too weak", HttpStatus.BAD_REQUEST),
    USER_004("User not found", HttpStatus.NOT_FOUND),
    USER_005("Display name already taken", HttpStatus.CONFLICT),
    USER_006("Cannot delete own account", HttpStatus.FORBIDDEN),

    // =========================================================================
    // MAIL - Email delivery errors
    // =========================================================================
    MAIL_001("Failed to send email", HttpStatus.SERVICE_UNAVAILABLE),
    MAIL_002("Email template not found", HttpStatus.INTERNAL_SERVER_ERROR),
    MAIL_003("Invalid email address", HttpStatus.BAD_REQUEST),
    MAIL_004("Email rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    MAIL_005("Verification token expired", HttpStatus.UNAUTHORIZED),
    MAIL_006("Verification token invalid", HttpStatus.UNAUTHORIZED),
    MAIL_007("Password reset token expired", HttpStatus.UNAUTHORIZED),
    MAIL_008("Password reset token invalid", HttpStatus.UNAUTHORIZED),

    // =========================================================================
    // RATE_LIMIT - Rate limiting errors
    // =========================================================================
    RATE_LIMIT_001("Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    RATE_LIMIT_002("Daily quota exceeded", HttpStatus.TOO_MANY_REQUESTS),
    RATE_LIMIT_003("API rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),

    // =========================================================================
    // SECURITY - Security and access control errors
    // =========================================================================
    SECURITY_001("CSRF validation failed", HttpStatus.FORBIDDEN),
    SECURITY_002("Suspicious activity detected", HttpStatus.FORBIDDEN),
    SECURITY_003("IP blocked", HttpStatus.FORBIDDEN),
    SECURITY_004("Invalid origin", HttpStatus.FORBIDDEN),
    SECURITY_005("Password compromised", HttpStatus.FORBIDDEN),

    // =========================================================================
    // SYSTEM - Internal system errors
    // =========================================================================
    SYSTEM_001("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    SYSTEM_002("Database connection error", HttpStatus.SERVICE_UNAVAILABLE),
    SYSTEM_003("Redis connection error", HttpStatus.SERVICE_UNAVAILABLE),
    SYSTEM_004("External service unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    SYSTEM_005("Configuration error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String message() {
        return message;
    }

    public String message(String customMessage) {
        return customMessage != null ? customMessage : message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
