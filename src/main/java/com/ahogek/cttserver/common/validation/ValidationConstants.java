package com.ahogek.cttserver.common.validation;

/**
 * Global validation regex and message constants pool.
 *
 * <p>Single source of truth for validation patterns to ensure consistency across the application.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public final class ValidationConstants {

    /**
     * Password strength: 8-32 characters, at least one uppercase, one lowercase, one digit, and one
     * special character.
     */
    // SonarQube false positive: this is a regex pattern, not a hardcoded password
    @SuppressWarnings("java:S2068")
    public static final String REGEX_PASSWORD =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,32}$";

    /** Strict UUID v4 format. */
    public static final String REGEX_UUID_V4 =
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    /**
     * Generic display name: 2-50 characters, allows Chinese, English letters, digits, underscores
     * and hyphens.
     */
    public static final String REGEX_DISPLAY_NAME = "^[\\u4e00-\\u9fa5a-zA-Z0-9_-]{2,50}$";

    public static final String MSG_EMAIL_INVALID = "Invalid email format";
    public static final String MSG_PASSWORD_WEAK =
            "Password must be 8-32 characters long, including uppercase, lowercase, number, and special character";
    public static final String MSG_UUID_INVALID = "Invalid UUID v4 format";
    public static final String MSG_NAME_INVALID =
            "Display name must be 2-50 characters and contain only letters, numbers, underscores, or hyphens";
    public static final String MSG_NOT_BLANK = "Field cannot be blank";
    public static final String MSG_PAGE_MIN = "Page number must be at least 1";
    public static final String MSG_PAGE_SIZE_MIN = "Page size must be at least 1";
    public static final String MSG_PAGE_SIZE_MAX = "Page size cannot exceed 100 to prevent OOM";

    private ValidationConstants() {}
}
