package com.ahogek.cttserver.fixtures;

import com.ahogek.cttserver.auth.entity.EmailVerificationToken;
import com.ahogek.cttserver.auth.entity.RefreshToken;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * JWT / Refresh Token test data factory.
 *
 * <p>Provides two types of data:
 *
 * <ul>
 *   <li>{@link TokenPair} - Valid access + refresh token pair (for Controller/Integration tests)
 *   <li>Boundary token strings (expired, malformed, invalid signature)
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Token pair for authentication tests
 * var tokens = TokenFixtures.validPairFor(userId);
 * mockMvc.perform(get("/api/users/me")
 *         .header("Authorization", tokens.bearerAccessToken()))
 *     .andExpect(status().isOk());
 *
 * // Expired token for boundary testing
 * var expired = TokenFixtures.expiredAccessPairFor(userId);
 *
 * // Malformed token for filter tests
 * mockMvc.perform(get("/api/users/me")
 *         .header("Authorization", TokenFixtures.MALFORMED_TOKEN))
 *     .andExpect(status().isUnauthorized());
 *
 * // RefreshToken entity (for Repository tests)
 * var token = TokenFixtures.refreshTokenBuilder()
 *         .userId(userId)
 *         .deviceId(deviceId)
 *         .expiresAt(Instant.now().plusSeconds(86400 * 14))
 *         .build();
 * }</pre>
 *
 * @author AhogeK
 * @since 2026-03-18
 */
public final class TokenFixtures {

    private TokenFixtures() {}

    // ==========================================
    // Pre-computed Boundary Tokens
    // ==========================================

    /** Malformed token (invalid JWT format). */
    public static final String MALFORMED_TOKEN = "not.a.valid.jwt";

    /** Token with invalid signature (valid format but wrong signature). */
    public static final String INVALID_SIGNATURE_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.INVALID_SIGNATURE";

    /** Empty token (missing Authorization header). */
    public static final String EMPTY_TOKEN = "";

    // ==========================================
    // TokenPair Record
    // ==========================================

    /**
     * Immutable record containing an access token and refresh token pair.
     *
     * <p>Bearer prefix is included in {@link #bearerAccessToken()} for Authorization header.
     *
     * @param accessToken access token string
     * @param refreshToken refresh token string
     * @param accessTokenExpiry access token expiry timestamp
     * @param refreshTokenExpiry refresh token expiry timestamp
     * @param userId associated user ID
     */
    public record TokenPair(
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiry,
            Instant refreshTokenExpiry,
            Long userId) {

        /** Returns access token with "Bearer " prefix for Authorization header. */
        public String bearerAccessToken() {
            return "Bearer " + accessToken;
        }

        /** Checks if refresh token is expired. */
        public boolean isRefreshExpired() {
            return Instant.now().isAfter(refreshTokenExpiry);
        }

        /** Checks if access token is expired. */
        public boolean isAccessExpired() {
            return Instant.now().isAfter(accessTokenExpiry);
        }
    }

    // ==========================================
    // Object Mother - Token Pairs
    // ==========================================

    /**
     * Creates a valid token pair for the specified user.
     *
     * <p>Token TTLs align with application-test.yaml settings:
     *
     * <ul>
     *   <li>Access token: 15 minutes
     *   <li>Refresh token: 14 days
     * </ul>
     *
     * @param userId user ID to associate with tokens
     * @return valid token pair
     */
    public static TokenPair validPairFor(Long userId) {
        var now = Instant.now();
        var accessTokenExpiry = now.plusSeconds(900); // 15min
        return tokenPair()
                .userId(userId)
                .accessToken(generateFakeJwt(userId, accessTokenExpiry))
                .refreshToken(UUID.randomUUID().toString())
                .accessTokenExpiry(accessTokenExpiry)
                .refreshTokenExpiry(now.plusSeconds(86400 * 14)) // 14d
                .build();
    }

    /**
     * Creates a token pair with expired access token.
     *
     * <p>Both JWT payload exp claim and TokenPair.expiry are set in the past for comprehensive
     * boundary testing.
     *
     * @param userId user ID to associate with tokens
     * @return token pair with expired access token
     */
    public static TokenPair expiredAccessPairFor(Long userId) {
        var now = Instant.now();
        var expiredAt = now.minusSeconds(1);
        return tokenPair()
                .userId(userId)
                .accessToken(generateFakeJwt(userId, expiredAt))
                .refreshToken(UUID.randomUUID().toString())
                .accessTokenExpiry(expiredAt)
                .refreshTokenExpiry(now.plusSeconds(86400 * 14))
                .build();
    }

    /**
     * Creates a token pair with both tokens expired.
     *
     * <p>Useful for testing re-authentication scenarios.
     *
     * @param userId user ID to associate with tokens
     * @return token pair with both tokens expired
     */
    public static TokenPair fullyExpiredPairFor(Long userId) {
        var now = Instant.now();
        var expiredAt = now.minusSeconds(1);
        return tokenPair()
                .userId(userId)
                .accessToken(generateFakeJwt(userId, expiredAt))
                .refreshToken(UUID.randomUUID().toString())
                .accessTokenExpiry(expiredAt)
                .refreshTokenExpiry(expiredAt)
                .build();
    }

    // ==========================================
    // Builder
    // ==========================================

    /** Creates a new token pair builder. */
    public static TokenPairBuilder tokenPair() {
        return new TokenPairBuilder();
    }

    /** Fluent builder for TokenPair. */
    public static final class TokenPairBuilder {
        private String accessToken = generateFakeJwt(0L, Instant.now().plusSeconds(900));
        private String refreshToken = UUID.randomUUID().toString();
        private Instant accessTokenExpiry = Instant.now().plusSeconds(900);
        private Instant refreshTokenExpiry = Instant.now().plusSeconds(86400 * 14);
        private Long userId = 0L;

        private TokenPairBuilder() {}

        public TokenPairBuilder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public TokenPairBuilder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public TokenPairBuilder accessTokenExpiry(Instant expiry) {
            this.accessTokenExpiry = expiry;
            return this;
        }

        public TokenPairBuilder refreshTokenExpiry(Instant expiry) {
            this.refreshTokenExpiry = expiry;
            return this;
        }

        public TokenPairBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public TokenPair build() {
            return new TokenPair(
                    accessToken, refreshToken, accessTokenExpiry, refreshTokenExpiry, userId);
        }
    }

    // ==========================================
    // RefreshToken Entity Builder (for Repository tests)
    // ==========================================

    /** Creates a new refresh token entity builder. */
    public static RefreshTokenBuilder refreshTokenBuilder() {
        return new RefreshTokenBuilder();
    }

    /** Fluent builder for RefreshToken entity. */
    public static final class RefreshTokenBuilder {
        private UUID userId;
        private UUID deviceId;
        private String tokenHash = UUID.randomUUID().toString();
        private Instant expiresAt = Instant.now().plusSeconds(86400 * 14);

        private RefreshTokenBuilder() {}

        public RefreshTokenBuilder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public RefreshTokenBuilder deviceId(UUID deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public RefreshTokenBuilder tokenHash(String tokenHash) {
            this.tokenHash = tokenHash;
            return this;
        }

        public RefreshTokenBuilder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /**
         * Builds the RefreshToken entity.
         *
         * <p>Note: createdAt is managed by JPA (@CreationTimestamp) and will be null until
         * persisted.
         *
         * @return new RefreshToken instance
         */
        public RefreshToken build() {
            var token = new RefreshToken();
            token.setUserId(userId);
            token.setDeviceId(deviceId);
            token.setTokenHash(tokenHash);
            token.setExpiresAt(expiresAt);
            return token;
        }
    }

    // ==========================================
    // EmailVerificationToken Entity Builder (for Repository tests)
    // ==========================================

    /** Creates a new email verification token entity builder. */
    public static EmailVerificationTokenBuilder emailVerificationTokenBuilder() {
        return new EmailVerificationTokenBuilder();
    }

    /** Fluent builder for EmailVerificationToken entity. */
    public static final class EmailVerificationTokenBuilder {
        private UUID userId;
        private String email = "test@example.com";
        private String tokenHash = UUID.randomUUID().toString();
        private String purpose = EmailVerificationToken.PURPOSE_REGISTER_VERIFY;
        private Instant expiresAt = Instant.now().plusSeconds(86400);
        private Instant consumedAt;
        private Instant revokedAt;

        private EmailVerificationTokenBuilder() {}

        public EmailVerificationTokenBuilder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public EmailVerificationTokenBuilder email(String email) {
            this.email = email;
            return this;
        }

        public EmailVerificationTokenBuilder tokenHash(String tokenHash) {
            this.tokenHash = tokenHash;
            return this;
        }

        public EmailVerificationTokenBuilder purpose(String purpose) {
            this.purpose = purpose;
            return this;
        }

        public EmailVerificationTokenBuilder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public EmailVerificationTokenBuilder consumedAt(Instant consumedAt) {
            this.consumedAt = consumedAt;
            return this;
        }

        public EmailVerificationTokenBuilder revokedAt(Instant revokedAt) {
            this.revokedAt = revokedAt;
            return this;
        }

        /**
         * Builds the EmailVerificationToken entity.
         *
         * <p>Note: createdAt is managed by JPA (@CreationTimestamp) and will be null until
         * persisted.
         *
         * @return new EmailVerificationToken instance
         */
        public EmailVerificationToken build() {
            var token = new EmailVerificationToken();
            token.setUserId(userId);
            token.setEmail(email);
            token.setTokenHash(tokenHash);
            token.setPurpose(purpose);
            token.setExpiresAt(expiresAt);
            token.setConsumedAt(consumedAt);
            token.setRevokedAt(revokedAt);
            return token;
        }
    }

    // ==========================================
    // Internal Utilities
    // ==========================================

    /**
     * Generates a structurally valid JWT with exp claim.
     *
     * <p><b>For Controller Slice tests only</b> - signature is not valid. Integration tests should
     * use real JwtService to generate tokens.
     *
     * @param userId user ID to embed in token subject
     * @param expiresAt expiration timestamp (exp claim)
     * @return JWT string with fake signature
     */
    private static String generateFakeJwt(Long userId, Instant expiresAt) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var header = encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes());
        var now = Instant.now();
        var payload =
                encoder.encodeToString(
                        ("{\"sub\":\""
                                        + userId
                                        + "\",\"iat\":"
                                        + now.getEpochSecond()
                                        + ",\"exp\":"
                                        + expiresAt.getEpochSecond()
                                        + "}")
                                .getBytes());
        return header + "." + payload + ".FAKE_SIGNATURE_FOR_SLICE_TEST";
    }
}
