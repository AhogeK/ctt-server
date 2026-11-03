package com.ahogek.cttserver.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * WebAuthn / passkey configuration.
 *
 * <p>The relying-party ID is a long-lived commitment: changing it invalidates every credential
 * already registered for it. Origins must be the exact browser origins allowed to complete
 * registration and assertion ceremonies.
 *
 * @param rpName human-readable relying-party name shown by authenticators
 * @param rpId relying-party ID (effective domain, e.g. {@code localhost} or {@code
 *     ctt.example.com})
 * @param allowedOrigins exact browser origins allowed to use the passkey endpoints
 * @param challengeTtl lifetime of a registration or assertion challenge
 * @param timeout browser ceremony timeout advertised in the options
 * @param newCredentialDelay optional delay before a newly registered credential may sign in
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Validated
@ConfigurationProperties(prefix = "ctt.security.passkey")
public record PasskeyProperties(
        @NotBlank @DefaultValue("Code Time Tracker") String rpName,
        @NotBlank @DefaultValue("localhost") String rpId,
        @NotEmpty @DefaultValue("http://localhost:5173") List<String> allowedOrigins,
        @NotNull @DefaultValue("5m") Duration challengeTtl,
        @NotNull @DefaultValue("5m") Duration timeout,
        @NotNull @DefaultValue("0s") Duration newCredentialDelay) {

    public PasskeyProperties {
        if (rpId == null
                || rpId.isBlank()
                || rpId.contains("://")
                || rpId.contains("/")
                || rpId.contains(":")) {
            throw new IllegalArgumentException(
                    "passkey rp-id must be a host without scheme, port or path");
        }
        if (challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalArgumentException("passkey challenge TTL must be positive");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("passkey ceremony timeout must be positive");
        }
        if (newCredentialDelay.isNegative()) {
            throw new IllegalArgumentException("passkey new-credential delay must not be negative");
        }
        for (String origin : allowedOrigins) {
            try {
                URI uri = URI.create(origin);
                if (!"http".equalsIgnoreCase(uri.getScheme())
                        && !"https".equalsIgnoreCase(uri.getScheme())) {
                    throw new IllegalArgumentException(
                            "passkey allowed origin must use http or https: " + origin);
                }
                if (uri.getHost() == null || uri.getHost().isBlank()) {
                    throw new IllegalArgumentException(
                            "passkey allowed origin must include a host: " + origin);
                }
                if ((uri.getRawPath() != null
                                && !uri.getRawPath().isEmpty()
                                && !"/".equals(uri.getRawPath()))
                        || uri.getRawQuery() != null
                        || uri.getRawFragment() != null) {
                    throw new IllegalArgumentException(
                            "passkey allowed origin must not include a path, query or fragment: "
                                    + origin);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "passkey allowed origin is invalid: " + origin, e);
            }
        }
    }
}
