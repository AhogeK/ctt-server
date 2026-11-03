package com.ahogek.cttserver.auth.passkey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Renames a registered passkey.
 *
 * @param name the new label
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
@Schema(description = "Rename a passkey")
public record PasskeyRenameRequest(
        @Schema(description = "New label", example = "Work laptop") @NotBlank @Size(max = 64)
                String name) {}
