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
    USER_ACCOUNT,
    OAUTH_ACCOUNT,
    DEVICE,
    API_KEY,
    CODING_SESSION,
    EMAIL_TOKEN,
    SYSTEM_CONFIG,
    UNKNOWN
}
