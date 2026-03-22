package com.ahogek.cttserver.auth.dto;

import com.ahogek.cttserver.common.validation.ValidationConstants;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Resend verification email request DTO.
 *
 * <p>Immutable record with Jakarta Validation annotations for input validation.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-22
 */
public record ResendVerificationRequest(
        @NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
                @Email(message = ValidationConstants.MSG_EMAIL_INVALID)
                String email) {

    /**
     * Compact constructor for normalization.
     *
     * <p>Normalizes email to lowercase and trims whitespace during deserialization.
     *
     * @param email the email address
     */
    public ResendVerificationRequest {
        email = (email == null) ? null : email.trim().toLowerCase();
    }
}
