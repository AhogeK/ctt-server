package com.ahogek.cttserver.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token generation and hashing utility.
 *
 * <p>Provides cryptographically secure token operations for email verification and other
 * authentication flows.
 *
 * <p><strong>Security Design:</strong>
 *
 * <ul>
 *   <li>Raw tokens are sent to users via email (transient, not stored)
 *   <li>Only SHA-256 hashes are stored in the database
 *   <li>This prevents token leakage from database compromise
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-22
 */
public final class TokenUtils {

    /** SecureRandom instance for token generation. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Token byte length (32 bytes = 256 bits). */
    private static final int TOKEN_BYTES = 32;

    private TokenUtils() {}

    /**
     * Generates a cryptographically strong random token.
     *
     * <p>Uses SecureRandom to generate 32 bytes of random data, encoded as URL-safe Base64 without
     * padding (43 characters).
     *
     * @return raw token string
     */
    public static String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Computes SHA-256 hash of the raw token.
     *
     * <p>Hash is stored in database to prevent token leakage from database compromise.
     *
     * @param rawToken the raw token string
     * @return hex-encoded SHA-256 hash (64 characters)
     */
    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts byte array to hex string.
     *
     * @param bytes the byte array
     * @return hex-encoded string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
