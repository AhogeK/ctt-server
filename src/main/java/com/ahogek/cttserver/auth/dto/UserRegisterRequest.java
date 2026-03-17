package com.ahogek.cttserver.auth.dto;

import com.ahogek.cttserver.common.validation.ValidationConstants;
import com.ahogek.cttserver.common.validation.annotation.StrongPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * User registration request DTO.
 *
 * <p>Immutable record with Jakarta Validation annotations for input validation.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public record UserRegisterRequest(
        @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Email(message = ValidationConstants.MSG_EMAIL_INVALID)
                String email,
        @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Pattern(
                        regexp = ValidationConstants.REGEX_DISPLAY_NAME,
                        message = ValidationConstants.MSG_NAME_INVALID)
                String displayName,
        @StrongPassword String password) {

    /**
     * Compact constructor for normalization.
     *
     * <p>Normalizes email to lowercase and trims whitespace during deserialization.
     *
     * @param email the email address
     * @param displayName the display name
     * @param password the password
     */
    public UserRegisterRequest {
        email = (email == null) ? null : email.trim().toLowerCase();
    }
}
