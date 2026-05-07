package com.ahogek.cttserver.common.config.properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Terms of service version configuration.
 *
 * <p>Tracks the current active terms version for user consent management.
 *
 * <p><strong>Configuration Prefix:</strong> {@code ctt.terms}
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-05-03
 */
@ConfigurationProperties(prefix = "ctt.terms")
public record TermsProperties(
        @Schema(
                        description = "Current active terms of service version identifier",
                        example = "1.0.0")
                String currentVersion,
        @Schema(
                        description = "Path for the terms acceptance endpoint",
                        example = "/api/v1/auth/terms/accept")
                String termsAcceptPath) {

    private static final Logger log = LoggerFactory.getLogger(TermsProperties.class);
    private static final String DEFAULT_VERSION = "1.0.0";

    public TermsProperties {
        if (currentVersion == null || currentVersion.isBlank()) {
            log.warn("terms.current-version not configured, defaulting to {}", DEFAULT_VERSION);
            currentVersion = DEFAULT_VERSION;
        }
        if (termsAcceptPath == null || termsAcceptPath.isBlank()) {
            termsAcceptPath = "/api/v1/auth/terms/accept";
        }
    }
}
