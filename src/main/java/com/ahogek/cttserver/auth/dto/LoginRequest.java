package com.ahogek.cttserver.auth.dto;

import com.ahogek.cttserver.common.validation.ValidationConstants;
import com.ahogek.cttserver.common.validation.annotation.StrongPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Login request DTO.
 *
 * @param email the user's email address
 * @param password the user's password
 * @param deviceId device ID for device binding
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-23
 */
@Schema(description = "Login request credentials")
public record LoginRequest(
        @Schema(description = "User email address", example = "user@example.com")
                @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Email(message = ValidationConstants.MSG_EMAIL_INVALID)
                String email,
        @Schema(
                        description =
                                "User password (min 8 chars, requires uppercase, lowercase, digit, and special char)",
                        example = "StrongPass123!")
                @StrongPassword
                String password,
        @Schema(description = "Device identifier for tracking", example = "device-123")
                @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                String deviceId) {

    public LoginRequest {
        email = (email == null) ? null : email.trim().toLowerCase();
    }
}
