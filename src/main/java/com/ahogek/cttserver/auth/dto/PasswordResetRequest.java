package com.ahogek.cttserver.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Password reset request DTO.
 *
 * @param email the email address to send reset link to
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-06
 */
public record PasswordResetRequest(
        @NotBlank(message = "Email cannot be blank") @Email(message = "Invalid email format")
                String email) {}
