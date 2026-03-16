package com.ahogek.cttserver.audit.enums;

/**
 * Resource types for audit events.
 *
 * <p>Maps to database tables or core domain models.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public enum ResourceType {

    /** User account entity. */
    USER,

    /** Email verification token entity. */
    EMAIL_VERIFICATION,

    /** Password reset token entity. */
    PASSWORD_RESET,

    /** Refresh token / long-term session entity. */
    REFRESH_TOKEN,

    /** Client / plugin API key entity. */
    API_KEY,

    /** Fallback type for system-level or uncategorized resources. */
    UNKNOWN
}
