package com.ahogek.cttserver.common.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Global security and risk management strategy baseline.
 *
 * <p>Consolidates scattered magic numbers and security thresholds into strongly-typed configuration
 * properties. Enables IDE auto-completion and supports cloud-native environment-based configuration
 * via ConfigMap.
 *
 * <p><strong>Configuration Prefix:</strong> {@code ctt.security}
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-18
 */
@Validated
@ConfigurationProperties(prefix = "ctt.security")
public record SecurityProperties(
        @NotNull JwtProperties jwt,
        @NotNull PasswordProperties password,
        @NotNull RateLimitProperties rateLimit,
        @NotNull AuditProperties audit,
        @NotNull Cors cors,
        @NotNull OAuthProperties oauth,
        @NotNull CookieProperties cookie,
        @NotNull ApiKeyProperties apiKey) {

    /** JWT and session configuration. */
    public record JwtProperties(
            @NotBlank String secretKey,
            @NotBlank String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("14d") Duration refreshTokenTtlPlugin,
            @DefaultValue("30d") Duration refreshTokenTtlWeb) {}

    /**
     * Password and account lockout strategy.
     *
     * <p><strong>Security Baseline:</strong> bcryptRounds minimum is set to 10 to prevent
     * dangerously weak configurations. BCrypt supports 4-31, but values below 10 are not
     * recommended for production use.
     */
    public record PasswordProperties(
            @Min(10) @DefaultValue("12") int bcryptRounds,
            @Min(3) @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("30m") Duration lockDuration,
            @DefaultValue("900") int failureWindowSeconds,
            @DefaultValue("PT720H") Duration retentionDuration,
            @DefaultValue("DB") String storage) {}

    /**
     * Global rate limiting fallback strategy.
     *
     * <p><strong>NOTE:</strong> Currently defined for future use. The fine-grained @RateLimit
     * annotation-based rate limiting is already implemented. This global fallback will be
     * integrated in a future phase for defense-in-depth.
     */
    public record RateLimitProperties(
            @DefaultValue("true") boolean enabled,
            @Min(1) @DefaultValue("200") int globalMaxRequestsPerSecond) {}

    /** Audit log masking strategy. */
    public record AuditProperties(
            @DefaultValue("true") boolean logPayloads,
            @DefaultValue(
                            "password,passwordConfirm,oldPassword,token,access_token,refresh_token,secret,key")
                    List<String> maskedFields) {}

    /** OAuth token encryption and provider settings. */
    public record OAuthProperties(
            String frontendUrl, String tokenEncryptionKey, GitHubProperties github) {

        /** GitHub OAuth provider configuration. */
        public record GitHubProperties(
                String clientId,
                String clientSecret,
                @DefaultValue("https://github.com/login/oauth/access_token") String tokenUri,
                @DefaultValue("https://api.github.com/user") String userInfoUri,
                @DefaultValue("https://api.github.com/user/emails") String userEmailsUri,
                @DefaultValue("read:user,user:email") String scope) {}
    }

    /**
     * Cross-Origin Resource Sharing (CORS) policy.
     *
     * <p>Defines which origins, methods, and headers are allowed to access the API. Applied to all
     * {@code /api/**} endpoints via {@link com.ahogek.cttserver.common.config.CorsConfig}.
     *
     * <p><strong>Production Note:</strong> Configure {@code allowedOrigins} and {@code
     * allowedOriginPatterns} restrictively to prevent unauthorized cross-origin access. When {@code
     * allowCredentials} is true, wildcard {@code "*"} origins are disallowed by the browser.
     */
    public record Cors(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedOriginPatterns,
            @NotEmpty List<String> allowedMethods,
            @NotEmpty List<String> allowedHeaders,
            @NotEmpty List<String> exposedHeaders,
            boolean allowCredentials,
            long maxAge) {}

    /**
     * Cookie configuration for authentication tokens.
     *
     * <p>Defines the {@code Path} attribute used when setting authentication cookies. The refresh
     * token cookie path is scoped narrowly to the refresh endpoint so the browser only sends the
     * cookie to that endpoint, reducing the blast radius if the cookie is leaked by other paths.
     */
    public record CookieProperties(
            @NotBlank @DefaultValue("/api/v1/auth/refresh") String refreshTokenPath) {}

    /**
     * API Key authentication configuration.
     *
     * <p>Configures the API Key authentication filter for JetBrains plugin and device clients.
     */
    public record ApiKeyProperties(
            @NotBlank @DefaultValue("Authorization") String headerName,
            @NotBlank @DefaultValue("Bearer") String headerPrefix,
            @DefaultValue("20") int maxKeysPerUser) {}
}
