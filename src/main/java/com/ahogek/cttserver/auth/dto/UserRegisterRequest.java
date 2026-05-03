package com.ahogek.cttserver.auth.dto;

import com.ahogek.cttserver.common.validation.ValidationConstants;
import com.ahogek.cttserver.common.validation.annotation.StrongPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * User registration request DTO.
 *
 * <p>Immutable record with Jakarta Validation annotations for input validation.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Schema(description = "User registration request")
public record UserRegisterRequest(
        @Schema(description = "User email address", example = "newuser@example.com")
                @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Email(message = ValidationConstants.MSG_EMAIL_INVALID)
                String email,
        @Schema(description = "User display name", example = "AhogeK")
                @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Pattern(
                        regexp = ValidationConstants.REGEX_DISPLAY_NAME,
                        message = ValidationConstants.MSG_NAME_INVALID)
                String displayName,
        @Schema(
                        description = "User password (8-64 characters, no complexity requirements)",
                        example = "SecurePass123!")
                @StrongPassword
                String password,
        @Schema(description = "Terms version accepted by user", example = "1.0.0")
                @NotBlank(message = "Terms version is required")
                String termsVersion) {

    /**
     * Compact constructor for normalization.
     *
     * <p>Normalizes email to lowercase and trims whitespace during deserialization.
     *
     * @param email the email address
     * @param displayName the display name
     * @param password the password
     * @param termsVersion the terms version accepted by user
     */
    public UserRegisterRequest {
        email = (email == null) ? null : email.trim().toLowerCase();
    }
}
