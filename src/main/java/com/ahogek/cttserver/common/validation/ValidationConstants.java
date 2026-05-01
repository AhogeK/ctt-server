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

    /** Minimum password length (NIST SP 800-63B compliant). */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /** Maximum password length (prevent storage of excessively long passwords). */
    public static final int PASSWORD_MAX_LENGTH = 64;

    /** Strict UUID v4 format. */
    public static final String REGEX_UUID_V4 =
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    /**
     * Generic display name: 2-50 characters, allows Chinese, Japanese (Hiragana/Katakana), Korean
     * (Hangul), English letters, digits, underscores and hyphens.
     */
    public static final String REGEX_DISPLAY_NAME =
            "^[\\u4e00-\\u9fa5\\u3040-\\u309f\\u30a0-\\u30ff\\uac00-\\ud7afa-zA-Z0-9_-]{2,50}$";

    /**
     * Allowed password characters: printable ASCII non-space (0x21-0x7E).
     * This is a regex pattern, not a hard-coded password.
     */
    @SuppressWarnings("java:S2068")
    public static final String REGEX_PASSWORD_CHARS = "^[!-~]+$";

    public static final String MSG_EMAIL_INVALID = "Invalid email format";
    public static final String MSG_PASSWORD_WEAK = "Password must be 8-64 characters long";
    public static final String MSG_PASSWORD_CHARS =
            "Password must only contain standard ASCII characters (letters, digits, and symbols)";
    public static final String MSG_UUID_INVALID = "Invalid UUID v4 format";
    public static final String MSG_NAME_INVALID =
            "Display name must be 2-50 characters and contain only Chinese, Japanese, Korean, English letters, numbers, underscores, or hyphens";
    public static final String MSG_NOT_BLANK = "Field cannot be blank";
    public static final String MSG_PAGE_MIN = "Page number must be at least 1";
    public static final String MSG_PAGE_SIZE_MIN = "Page size must be at least 1";
    public static final String MSG_PAGE_SIZE_MAX = "Page size cannot exceed 100 to prevent OOM";

    private ValidationConstants() {}
}
