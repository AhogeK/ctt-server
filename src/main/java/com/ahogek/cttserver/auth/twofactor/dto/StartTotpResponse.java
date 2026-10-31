package com.ahogek.cttserver.auth.twofactor.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Unconfirmed TOTP setup material.
 *
 * @param secret Base32 secret, shown only before confirmation
 * @param otpauthUri URI for local QR rendering
 * @param digits TOTP digits
 * @param period TOTP step in seconds
 * @param issuer issuer label
 * @param expiresAt when the pending setup expires
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Schema(description = "Unconfirmed TOTP setup material")
public record StartTotpResponse(
        @Schema(
                        description = "Base32 secret, shown only before confirmation",
                        example = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP")
                String secret,
        @Schema(
                        description = "otpauth URI for local QR rendering",
                        example =
                                "otpauth://totp/Code%20Time%20Tracker:user%40example.com?secret=...")
                String otpauthUri,
        @Schema(description = "TOTP digits", example = "6") int digits,
        @Schema(description = "TOTP step in seconds", example = "30") int period,
        @Schema(description = "Issuer label", example = "Code Time Tracker") String issuer,
        @Schema(description = "When the pending setup expires", example = "2026-10-30T10:40:00Z")
                Instant expiresAt) {}
