package com.ahogek.cttserver.common.config.properties;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        @Schema(description = "Current active terms of service version identifier", example = "1.0.0")
        String currentVersion) {}
