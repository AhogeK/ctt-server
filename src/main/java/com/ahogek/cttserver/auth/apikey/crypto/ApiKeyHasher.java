package com.ahogek.cttserver.auth.apikey.crypto;

import com.ahogek.cttserver.common.utils.TokenUtils;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates and hashes API keys for programmatic authentication (IDE plugins, CLIs).
 *
 * <p><strong>Key Format:</strong> {@code cttak_<prefix>_<secret>}
 *
 * <ul>
 *   <li>{@code prefix} — 8 chars of URL-safe Base64 (6 random bytes); used as the visible {@code
 *       key_prefix} for audit log identification
 *   <li>{@code secret} — 32 chars of URL-safe Base64 (24 random bytes); the actual secret material
 * </ul>
 *
 * <p><strong>Security Design:</strong>
 *
 * <ul>
 *   <li>Raw keys are returned to callers exactly once; only SHA-256 hashes are persisted
 *   <li>{@link SecureRandom} is used for both prefix and secret bytes
 *   <li>This class never logs raw key material — callers must not log either
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
@Component
public class ApiKeyHasher {

    public static final String KEY_PREFIX_MARKER = "cttak_";

    private static final int PREFIX_BYTES = 6;

    private static final int SECRET_BYTES = 24;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a fresh raw API key in {@code cttak_<prefix>_<secret>} format.
     *
     * @return raw API key string; callers must hand it to the user exactly once and never log it
     */
    public String generateRawKey() {
        String prefix = randomBase64Url(PREFIX_BYTES);
        String secret = randomBase64Url(SECRET_BYTES);
        return KEY_PREFIX_MARKER + prefix + "_" + secret;
    }

    /**
     * Computes the SHA-256 hash of a raw API key for at-rest storage.
     *
     * <p>Delegates to {@link TokenUtils#hashToken(String)} to keep hashing consistent with refresh
     * and email verification tokens.
     *
     * @param rawKey the raw API key produced by {@link #generateRawKey()}
     * @return 64-character lowercase hex SHA-256 hash
     */
    public String hashKey(String rawKey) {
        return TokenUtils.hashToken(rawKey);
    }

    private String randomBase64Url(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
