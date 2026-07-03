package com.ahogek.cttserver.mail.template;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Template data for email change verification emails.
 *
 * @param username the recipient's username for personalization
 * @param newEmail the new email address being changed to
 * @param verificationLink the verification URL to confirm the email change
 * @param expiresIn the duration until the verification link expires
 * @author AhogeK
 * @since 2026-07-03
 */
public record ChangeEmailTemplateData(
        String username, String newEmail, String verificationLink, Duration expiresIn)
        implements MailTemplateData {

    /** Validates that all required fields are non-null. */
    public ChangeEmailTemplateData {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(newEmail, "newEmail must not be null");
        Objects.requireNonNull(verificationLink, "verificationLink must not be null");
        Objects.requireNonNull(expiresIn, "expiresIn must not be null");
    }

    @Override
    public String getTemplateName() {
        return "change-email";
    }

    @Override
    public Map<String, Object> getVariables() {
        return Map.of(
                "username", username,
                "newEmail", newEmail,
                "verificationLink", verificationLink,
                "expirationMinutes", expiresIn.toMinutes());
    }
}
