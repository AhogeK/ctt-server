package com.ahogek.cttserver.audit.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTypeTest {

    @Test
    void enum_values_exist() {
        assertThat(ResourceType.values()).hasSize(8);
        assertThat(ResourceType.values())
                .containsExactly(
                        ResourceType.USER_ACCOUNT,
                        ResourceType.OAUTH_ACCOUNT,
                        ResourceType.DEVICE,
                        ResourceType.API_KEY,
                        ResourceType.CODING_SESSION,
                        ResourceType.EMAIL_TOKEN,
                        ResourceType.SYSTEM_CONFIG,
                        ResourceType.UNKNOWN);
    }

    @Test
    void value_of_returns_correct_enum() {
        assertThat(ResourceType.valueOf("USER_ACCOUNT")).isEqualTo(ResourceType.USER_ACCOUNT);
        assertThat(ResourceType.valueOf("API_KEY")).isEqualTo(ResourceType.API_KEY);
        assertThat(ResourceType.valueOf("UNKNOWN")).isEqualTo(ResourceType.UNKNOWN);
    }
}
