package com.ahogek.cttserver.common.config.properties;

import com.ahogek.cttserver.common.BaseIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding and validation tests for the two-factor configuration.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-30
 */
@BaseIntegrationTest
@DisplayName("TwoFactorProperties binding")
class TwoFactorPropertiesTest {

    @Autowired private TwoFactorProperties properties;

    @Test
    @DisplayName("Should bind the documented defaults")
    void shouldBindDefaults_whenConfigurationIsLoaded() {
        assertThat(properties.issuer()).isEqualTo("Code Time Tracker");
        assertThat(properties.digits()).isEqualTo(6);
        assertThat(properties.periodSeconds()).isEqualTo(30);
        assertThat(properties.windowSteps()).isEqualTo(1);
        assertThat(properties.pendingSetupTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.challengeTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.recentAuthenticationWindow()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.recoveryCodeCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should decode the encryption key to exactly 32 bytes")
    void shouldDecodeEncryptionKey_whenConfigurationIsLoaded() {
        assertThat(Base64.getDecoder().decode(properties.encryptionKey())).hasSize(32);
    }
}
