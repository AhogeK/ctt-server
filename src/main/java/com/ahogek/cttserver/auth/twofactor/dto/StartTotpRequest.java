package com.ahogek.cttserver.auth.twofactor.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request to start a TOTP setup.
 *
 * @param currentPassword current password for password accounts; null for OAuth-only accounts
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Schema(description = "Start a TOTP setup")
public record StartTotpRequest(
        @Schema(description = "Current password; required for password accounts", nullable = true)
                @Size(max = 128)
                String currentPassword) {}
