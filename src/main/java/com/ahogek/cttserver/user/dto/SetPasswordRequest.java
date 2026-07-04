package com.ahogek.cttserver.user.dto;

import com.ahogek.cttserver.common.validation.annotation.StrongPassword;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to set password for OAuth users")
public record SetPasswordRequest(
        @Schema(description = "New password", example = "NewSecurePass123!")
                @NotBlank(message = "Password is required")
                @StrongPassword
                String newPassword) {}
