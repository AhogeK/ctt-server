package com.ahogek.cttserver.mail.template;

import java.util.Map;
import java.util.Objects;

/**
 * Template data for the security notification sent after a passkey is registered.
 *
 * @param username the recipient's display name
 * @param passkeyName the label chosen for the new credential
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
public record PasskeyAddedTemplateData(String username, String passkeyName)
        implements MailTemplateData {

    public PasskeyAddedTemplateData {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passkeyName, "passkeyName must not be null");
    }

    @Override
    public String getTemplateName() {
        return "passkey-added";
    }

    @Override
    public Map<String, Object> getVariables() {
        return Map.of("username", username, "passkeyName", passkeyName);
    }
}
