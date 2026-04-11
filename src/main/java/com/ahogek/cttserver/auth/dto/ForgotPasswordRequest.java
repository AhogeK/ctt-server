package com.ahogek.cttserver.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Forgot password request carrier.
 *
 * @param email the email address to send reset link to
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-07
 */
@Schema(description = "Forgot password request with email address")
public record ForgotPasswordRequest(
        @Schema(
                        description = "Email address to send password recovery link to",
                        example = "user@example.com")
                @NotBlank(message = "Email cannot be blank")
                @Email(message = "Invalid email format")
                String email) {}
