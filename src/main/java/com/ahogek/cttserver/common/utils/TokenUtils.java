package com.ahogek.cttserver.common.utils;

import com.ahogek.cttserver.auth.entity.EmailVerificationToken;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.auth.repository.EmailVerificationTokenRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Token generation and hashing utility.
 *
 * <p>Provides cryptographically secure token operations for email verification, refresh tokens, and
 * other authentication flows.
 *
 * <p><strong>Security Design:</strong>
 *
 * <ul>
 *   <li>Raw tokens are sent to users via email or HTTP response (transient, not stored)
 *   <li>Only SHA-256 hashes are stored in the database
 *   <li>This prevents token leakage from database compromise
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-22
 */
public final class TokenUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    /**
     * Holds an email verification token entity and its raw (unhashed) value.
     *
     * @param token the persisted token entity
     * @param rawToken the raw token string (transient, not stored)
     */
    public record EmailVerificationTokenPair(EmailVerificationToken token, String rawToken) {}

    /**
     * Creates and persists a verification token for a user.
     *
     * <p>Generates a cryptographically secure token, hashes it, persists the hash, and returns both
     * the persisted entity and the raw token for email delivery.
     *
     * @param userId the user ID
     * @param email the user's email address
     * @param ttl time-to-live for the token
     * @param tokenRepository the repository for persisting tokens
     * @return EmailVerificationTokenPair containing the persisted token and raw token
     */
    public static EmailVerificationTokenPair createVerificationToken(
            UUID userId,
            String email,
            Duration ttl,
            EmailVerificationTokenRepository tokenRepository) {
        String rawToken = generateRawToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(userId);
        token.setEmail(email);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(Instant.now().plus(ttl));
        tokenRepository.save(token);
        return new EmailVerificationTokenPair(token, rawToken);
    }

    /**
     * Holds a refresh token entity and its raw (unhashed) value.
     *
     * @param token the persisted token entity
     * @param rawToken the raw token string (transient, not stored)
     */
    public record RefreshTokenPair(RefreshToken token, String rawToken) {}

    /**
     * Creates and persists a refresh token for a user.
     *
     * <p>Generates a cryptographically secure token, hashes it, persists the hash, and returns both
     * the persisted entity and the raw token for client delivery.
     *
     * @param userId the user ID
     * @param issuedFor the token audience ("WEB" or "PLUGIN")
     * @param ttl time-to-live for the token
     * @param deviceId optional device ID for device binding
     * @param repository the repository for persisting tokens
     * @return RefreshTokenPair containing the persisted token and raw token
     */
    public static RefreshTokenPair createRefreshToken(
            UUID userId,
            String issuedFor,
            Duration ttl,
            UUID deviceId,
            RefreshTokenRepository repository) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setIssuedFor(issuedFor);
        token.setExpiresAt(Instant.now().plus(ttl));
        token.setDeviceId(deviceId);

        repository.save(token);
        return new RefreshTokenPair(token, rawToken);
    }
}
