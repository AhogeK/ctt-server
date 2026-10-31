package com.ahogek.cttserver.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Completes a second-factor login challenge.
 *
 * @param challengeToken the token returned by the password login
 * @param code a TOTP code or an unused recovery code
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Schema(description = "Complete a second-factor login")
public record LoginTwoFactorRequest(
        @Schema(description = "One-time challenge token") @NotBlank @Size(max = 256)
                String challengeToken,
        @Schema(description = "TOTP code or recovery code") @NotBlank @Size(max = 64)
                String code) {}
