package com.ahogek.cttserver.auth.twofactor;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema contract for the two-factor columns and recovery-code table.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@BaseIntegrationTest
@DisplayName("Two-factor schema")
class TwoFactorSchemaIntegrationTest {

    @Autowired private JdbcClient jdbcClient;

    @Test
    @DisplayName("Should add the three nullable columns to users")
    void shouldAddTwoFactorColumns_whenMigrationsRun() {
        List<String> columns =
                jdbcClient
                        .sql(
                                "SELECT column_name FROM information_schema.columns"
                                        + " WHERE table_name = 'users'")
                        .query(String.class)
                        .list();

        assertThat(columns)
                .contains("totp_secret_ciphertext", "totp_confirmed_at", "totp_last_used_step");
    }

    @Test
    @DisplayName("Should create the recovery-code table with its constraints")
    void shouldCreateRecoveryCodeTable_whenMigrationsRun() {
        List<String> columns =
                jdbcClient
                        .sql(
                                "SELECT column_name FROM information_schema.columns"
                                        + " WHERE table_name = 'user_recovery_codes'")
                        .query(String.class)
                        .list();

        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "id", "user_id", "code_hash", "batch_id", "created_at", "used_at");
    }
}
