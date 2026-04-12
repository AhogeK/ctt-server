package com.ahogek.cttserver.audit.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTypeTest {

    @Test
    void enum_values_exist() {
        assertThat(ResourceType.values()).hasSize(8);
        assertThat(ResourceType.values())
                .containsExactly(
                        ResourceType.USER,
                        ResourceType.EMAIL_VERIFICATION,
                        ResourceType.PASSWORD_RESET,
                        ResourceType.REFRESH_TOKEN,
                        ResourceType.API_KEY,
                        ResourceType.OAUTH_ACCOUNT,
                        ResourceType.MAIL_OUTBOX,
                        ResourceType.UNKNOWN);
    }

    @Test
    void value_of_returns_correct_enum() {
        assertThat(ResourceType.valueOf("USER")).isEqualTo(ResourceType.USER);
        assertThat(ResourceType.valueOf("EMAIL_VERIFICATION"))
                .isEqualTo(ResourceType.EMAIL_VERIFICATION);
        assertThat(ResourceType.valueOf("PASSWORD_RESET")).isEqualTo(ResourceType.PASSWORD_RESET);
        assertThat(ResourceType.valueOf("REFRESH_TOKEN")).isEqualTo(ResourceType.REFRESH_TOKEN);
        assertThat(ResourceType.valueOf("API_KEY")).isEqualTo(ResourceType.API_KEY);
        assertThat(ResourceType.valueOf("OAUTH_ACCOUNT")).isEqualTo(ResourceType.OAUTH_ACCOUNT);
        assertThat(ResourceType.valueOf("MAIL_OUTBOX")).isEqualTo(ResourceType.MAIL_OUTBOX);
        assertThat(ResourceType.valueOf("UNKNOWN")).isEqualTo(ResourceType.UNKNOWN);
    }
}
