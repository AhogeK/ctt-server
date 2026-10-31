package com.ahogek.cttserver.common.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

/**
 * Two-factor authentication configuration.
 *
 * <p>The encryption key is a base64-encoded 32-byte AES key. It is validated at startup so a
 * missing or malformed key fails fast instead of silently disabling encryption.
 *
 * @param issuer issuer shown by authenticator applications
 * @param encryptionKey base64-encoded 32-byte AES key
 * @param digits TOTP digits
 * @param periodSeconds TOTP step in seconds
 * @param windowSteps accepted steps before and after the current step
 * @param pendingSetupTtl lifetime of an unconfirmed secret
 * @param challengeTtl lifetime of a login challenge
 * @param recentAuthenticationWindow maximum age of a session for OAuth-only re-authentication
 * @param recoveryCodeCount number of recovery codes issued per batch
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Validated
@ConfigurationProperties(prefix = "ctt.security.two-factor")
public record TwoFactorProperties(
        @NotBlank @DefaultValue("Code Time Tracker") String issuer,
        String encryptionKey,
        @Min(6) @Max(8) @DefaultValue("6") int digits,
        @Min(15) @DefaultValue("30") int periodSeconds,
        @Min(0) @Max(2) @DefaultValue("1") int windowSteps,
        @NotNull @DefaultValue("10m") Duration pendingSetupTtl,
        @NotNull @DefaultValue("5m") Duration challengeTtl,
        @NotNull @DefaultValue("10m") Duration recentAuthenticationWindow,
        @Min(1) @DefaultValue("10") int recoveryCodeCount) {

    public TwoFactorProperties {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException(
                    "two-factor encryption key must be configured (TOTP_ENCRYPTION_KEY)");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encryptionKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("two-factor encryption key must be valid base64", e);
        }
        if (key.length != 32) {
            throw new IllegalArgumentException(
                    "two-factor encryption key must decode to 32 bytes, got " + key.length);
        }
    }
}
