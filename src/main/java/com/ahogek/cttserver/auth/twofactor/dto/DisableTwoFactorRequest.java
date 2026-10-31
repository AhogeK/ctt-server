package com.ahogek.cttserver.auth.twofactor.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Disables two-factor authentication.
 *
 * @param code TOTP code, mutually exclusive with {@code recoveryCode}
 * @param recoveryCode unused recovery code, mutually exclusive with {@code code}
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Schema(description = "Disable two-factor authentication")
public record DisableTwoFactorRequest(
        @Schema(description = "TOTP code", nullable = true) @Size(max = 32) String code,
        @Schema(description = "Unused recovery code", nullable = true) @Size(max = 64)
                String recoveryCode) {}
