package com.ahogek.cttserver.mail.template;

import java.util.Map;

/**
 * Marker interface for strongly-typed email template data objects.
 *
 * <p>This sealed interface ensures compile-time type safety for template rendering, preventing
 * runtime errors from missing or misspelled variable names.
 *
 * @author AhogeK
 * @since 2026-03-19
 */
public sealed interface MailTemplateData
        permits EmailVerificationTemplateData, PasswordResetTemplateData {

    /**
     * Returns the template name without extension.
     *
     * @return template name (e.g., "email-verification", "password-reset")
     */
    String getTemplateName();

    /**
     * Returns the variables to be injected into the template.
     *
     * @return immutable map of variable names to values
     */
    Map<String, Object> getVariables();
}
