package com.ahogek.cttserver.auth.dto;

import com.ahogek.cttserver.common.validation.ValidationConstants;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request DTO.
 *
 * @param email the user's email address
 * @param password the user's password
 * @param deviceId optional device ID for device binding
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-23
 */
public record LoginRequest(
        @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Email(message = ValidationConstants.MSG_EMAIL_INVALID)
                String email,
        @NotBlank(message = ValidationConstants.MSG_NOT_BLANK) String password,
        String deviceId) {

    public LoginRequest {
        email = (email == null) ? null : email.trim().toLowerCase();
    }
}
