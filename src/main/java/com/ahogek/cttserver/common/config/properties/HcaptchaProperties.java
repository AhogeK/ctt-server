package com.ahogek.cttserver.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * hCaptcha verification configuration.
 *
 * <p>Provides type-safe access to hCaptcha integration settings for bot protection on sensitive
 * endpoints such as registration and password reset.
 *
 * <p><strong>Configuration Prefix:</strong> {@code ctt.security.hcaptcha}
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-05-23
 */
@ConfigurationProperties(prefix = "ctt.security.hcaptcha")
public record HcaptchaProperties(
        @Schema(
                        description = "hCaptcha site key for frontend widget rendering",
                        example = "10000000-ffff-ffff-ffff-000000000001")
                String siteKey,
        @Schema(
                        description = "hCaptcha secret key for server-side verification",
                        example = "0x0000000000000000000000000000000000000000")
                String secretKey,
        @Schema(
                        description = "hCaptcha verification API endpoint",
                        example = "https://api.hcaptcha.com/siteverify")
                @DefaultValue("https://api.hcaptcha.com/siteverify")
                String verifyUrl,
        @Schema(description = "Timeout for hCaptcha verification HTTP request", example = "5s")
                @DefaultValue("5s")
                Duration timeout) {}
