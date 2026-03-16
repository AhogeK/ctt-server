package com.ahogek.cttserver.common.utils;

import java.util.Set;

/**
 * Sensitive data desensitization utility.
 *
 * <p>Provides masking methods for headers, emails, passwords, tokens, and other sensitive
 * information. This is the first line of defense in the three-layer desensitization architecture.
 *
 * <p><strong>Usage in RequestLoggingFilter:</strong>
 *
 * <pre>{@code
 * String headerValue = request.getHeader("Authorization");
 * String maskedValue = DesensitizeUtils.maskHeader("Authorization", headerValue);
 * log.atDebug().addKeyValue("header_Authorization", maskedValue).log("Request header");
 * }</pre>
 *
 * <p><strong>Three-Layer Defense:</strong>
 *
 * <ul>
 *   <li>Layer 1 (Filter): Use this utility to mask headers before logging
 *   <li>Layer 2 (DTO): Use {@code @JsonSerialize(using = MaskSerializer.class)} on fields
 *   <li>Layer 3 (Global): Logback {@code MaskingMessageConverter} provides regex-based fallback
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public final class DesensitizeUtils {

    /** Standard mask pattern for sensitive values. */
    private static final String MASK_PATTERN = "******";

    /** Headers that must be masked (case-insensitive). */
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of("authorization", "cookie", "set-cookie", "x-api-key", "token", "x-auth-token");

    private DesensitizeUtils() {}

    /**
     * Masks sensitive HTTP header values.
     *
     * @param headerName the header name
     * @param value the header value
     * @return masked value if sensitive, original value otherwise
     */
    public static String maskHeader(String headerName, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (SENSITIVE_HEADERS.contains(headerName.toLowerCase())) {
            return MASK_PATTERN + "(REDACTED)";
        }
        return value;
    }

    /**
     * Masks email addresses, preserving first 2 chars and domain.
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>{@code user@example.com} → {@code us***@example.com}
     *   <li>{@code a@b.com} → {@code ***@b.com}
     * </ul>
     *
     * @param email the email address
     * @return masked email or original if invalid
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }

    /**
     * Masks password or token strings completely.
     *
     * @param value the sensitive value
     * @return masked value or null if input is null
     */
    public static String maskPassword(String value) {
        if (value == null) {
            return null;
        }
        return MASK_PATTERN;
    }

    /**
     * Masks a token, showing only first and last 4 chars.
     *
     * <p>Example: {@code eyJhbGciOiJIUzI1NiJ9...xyz} → {@code eyJh...xyz}
     *
     * @param token the token value
     * @param maskLength minimum length to apply partial masking
     * @return masked token
     */
    public static String maskToken(String token, int maskLength) {
        if (token == null || token.length() < maskLength) {
            return MASK_PATTERN;
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    /**
     * Masks a generic string with partial visibility.
     *
     * @param value the value to mask
     * @param visiblePrefix number of chars to show at start
     * @param visibleSuffix number of chars to show at end
     * @return masked string
     */
    public static String maskPartial(String value, int visiblePrefix, int visibleSuffix) {
        if (value == null) {
            return null;
        }
        if (value.length() <= visiblePrefix + visibleSuffix) {
            return MASK_PATTERN;
        }
        return value.substring(0, visiblePrefix)
                + "***"
                + value.substring(value.length() - visibleSuffix);
    }
}
