package com.ahogek.cttserver.mail.template;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Template data for email verification emails.
 *
 * @param username the recipient's username for personalization
 * @param verificationLink the verification URL to activate the account
 * @param expiresIn the duration until the verification link expires
 * @author AhogeK
 * @since 2026-03-19
 */
public record EmailVerificationTemplateData(
        String username, String verificationLink, Duration expiresIn) implements MailTemplateData {

    /** Validates that all required fields are non-null. */
    public EmailVerificationTemplateData {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(verificationLink, "verificationLink must not be null");
        Objects.requireNonNull(expiresIn, "expiresIn must not be null");
    }

    @Override
    public String getTemplateName() {
        return "email-verification";
    }

    @Override
    public Map<String, Object> getVariables() {
        return Map.of(
                "username", username,
                "verificationLink", verificationLink,
                "expirationMinutes", expiresIn.toMinutes());
    }
}
