package com.ahogek.cttserver.auth.dto;

import com.ahogek.cttserver.common.validation.annotation.StrongPassword;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reset password request carrier.
 *
 * @param token the reset token from email link
 * @param newPassword the new password (must meet strong password requirements)
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-07
 */
@Schema(description = "Reset password confirmation with token and new password")
public record ResetPasswordRequest(
        @Schema(description = "Password reset token from email link", example = "a1b2c3d4e5f6...")
                @NotBlank(message = "Token cannot be blank")
                String token,
        @Schema(
                        description = "New password (8-64 characters, no complexity requirements)",
                        example = "NewSecurePass123!")
                @StrongPassword
                String newPassword) {}
