package com.ahogek.cttserver.auth.twofactor.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Current two-factor state for the authenticated account.
 *
 * @param enabled whether TOTP is confirmed
 * @param confirmedAt when the current secret was confirmed
 * @param recoveryCodesRemaining number of unused recovery codes
 * @param hasPassword whether the account has a password
 * @param recentAuthentication whether the current session is recent enough for OAuth-only setup
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Schema(description = "Two-factor authentication status")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TwoFactorStatusResponse(
        @Schema(description = "Whether TOTP is enabled", example = "true") boolean enabled,
        @Schema(
                        description = "When the current TOTP secret was confirmed",
                        example = "2026-10-30T10:30:00Z",
                        nullable = true)
                Instant confirmedAt,
        @Schema(description = "Number of unused recovery codes", example = "10")
                long recoveryCodesRemaining,
        @Schema(description = "Whether the account has a password", example = "true")
                boolean hasPassword,
        @Schema(
                        description = "Whether this session is recent enough for OAuth-only setup",
                        example = "true")
                boolean recentAuthentication) {}
