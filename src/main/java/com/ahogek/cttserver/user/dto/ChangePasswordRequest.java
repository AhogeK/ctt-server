package com.ahogek.cttserver.user.dto;

import com.ahogek.cttserver.common.validation.annotation.StrongPassword;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to change password for authenticated users")
public record ChangePasswordRequest(
        @Schema(description = "Current password", example = "CurrentPass123!")
                @NotBlank(message = "Current password is required")
                String currentPassword,
        @Schema(description = "New password", example = "NewSecurePass123!")
                @NotBlank(message = "Password is required")
                @StrongPassword
                String newPassword) {}
