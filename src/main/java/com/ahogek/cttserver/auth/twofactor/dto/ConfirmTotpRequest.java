package com.ahogek.cttserver.auth.twofactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Confirms a pending TOTP secret.
 *
 * @param code six-digit TOTP code
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@Schema(description = "Confirm a pending TOTP secret")
public record ConfirmTotpRequest(
        @Schema(description = "Six-digit TOTP code", example = "123456") @NotBlank @Size(max = 32)
                String code) {}
