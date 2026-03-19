package com.ahogek.cttserver.mail.template;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Template data for password reset emails.
 *
 * @param username the recipient's username for personalization
 * @param resetLink the password reset URL with token
 * @param expiresIn the duration until the reset link expires
 * @author AhogeK
 * @since 2026-03-19
 */
public record PasswordResetTemplateData(String username, String resetLink, Duration expiresIn)
        implements MailTemplateData {

    /** Validates that all required fields are non-null. */
    public PasswordResetTemplateData {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(resetLink, "resetLink must not be null");
        Objects.requireNonNull(expiresIn, "expiresIn must not be null");
    }

    @Override
    public String getTemplateName() {
        return "password-reset";
    }

    @Override
    public Map<String, Object> getVariables() {
        return Map.of(
                "username", username,
                "resetLink", resetLink,
                "expirationMinutes", expiresIn.toMinutes());
    }
}
