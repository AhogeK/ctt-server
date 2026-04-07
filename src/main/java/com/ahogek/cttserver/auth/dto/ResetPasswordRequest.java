package com.ahogek.cttserver.auth.dto;

import com.ahogek.cttserver.common.validation.annotation.StrongPassword;

import jakarta.validation.constraints.NotBlank;

/**
 * Reset password request carrier.
 *
 * @param token the reset token from email link
 * @param newPassword the new password (must meet strong password requirements)
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-07
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Token cannot be blank") String token,
        @StrongPassword String newPassword) {}
