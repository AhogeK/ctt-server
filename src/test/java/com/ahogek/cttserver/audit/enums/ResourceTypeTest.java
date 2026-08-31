package com.ahogek.cttserver.audit.enums;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTypeTest {

    @Test
    void enum_values_exist() {
        assertThat(ResourceType.values()).hasSize(11);
        assertThat(ResourceType.values())
                .containsExactly(
                        ResourceType.USER,
                        ResourceType.EMAIL_VERIFICATION,
                        ResourceType.PASSWORD_RESET,
                        ResourceType.REFRESH_TOKEN,
                        ResourceType.API_KEY,
                        ResourceType.OAUTH_ACCOUNT,
                        ResourceType.MAIL_OUTBOX,
                        ResourceType.CODING_SESSION,
                        ResourceType.DEVICE,
                        ResourceType.ACHIEVEMENT,
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
        assertThat(ResourceType.valueOf("CODING_SESSION")).isEqualTo(ResourceType.CODING_SESSION);
        assertThat(ResourceType.valueOf("DEVICE")).isEqualTo(ResourceType.DEVICE);
        assertThat(ResourceType.valueOf("ACHIEVEMENT")).isEqualTo(ResourceType.ACHIEVEMENT);
        assertThat(ResourceType.valueOf("UNKNOWN")).isEqualTo(ResourceType.UNKNOWN);
    }

    @Test
    void audit_constraint_covers_all_resource_types() throws IOException {
        // The constraint was extended by a standalone migration (the init migration is never
        // edited once applied), so the effective constraint is the ADD CONSTRAINT in that file.
        String sql =
                readMigration("/db/migration/V20260303210000__init_base_schema.sql")
                        + "\n"
                        + readMigration(
                                "/db/migration/V20260831230001__add_achievement_audit_resource_type.sql");
        Matcher matcher =
                Pattern.compile(
                                "ADD CONSTRAINT chk_audit_resource_type\\s+CHECK\\s*\\(resource_type IN\\s*\\(([^)]*)\\)\\)",
                                Pattern.DOTALL)
                        .matcher(sql);
        assertThat(matcher.find())
                .as("audit resource type constraint not found in the standalone migration")
                .isTrue();
        String allowed = matcher.group(1);
        for (ResourceType type : ResourceType.values()) {
            assertThat(allowed)
                    .as("migration CHECK constraint is missing %s", type)
                    .contains("'" + type.name() + "'");
        }
    }

    private static String readMigration(String resource) throws IOException {
        try (InputStream in = ResourceTypeTest.class.getResourceAsStream(resource)) {
            assertThat(in)
                    .as("migration resource %s must exist on the classpath", resource)
                    .isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
