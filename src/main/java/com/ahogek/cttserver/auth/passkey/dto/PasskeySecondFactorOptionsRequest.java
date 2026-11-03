package com.ahogek.cttserver.auth.passkey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requests passkey assertion options for an existing second-factor login challenge.
 *
 * @param challengeToken the one-time challenge returned by the password or OAuth login
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Schema(description = "Passkey options for a second-factor login challenge")
public record PasskeySecondFactorOptionsRequest(
        @Schema(description = "One-time second-factor challenge token") @NotBlank @Size(max = 256)
                String challengeToken) {}
