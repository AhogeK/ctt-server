package com.ahogek.cttserver.auth.apikey.enums;

import com.ahogek.cttserver.auth.apikey.entity.ApiKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKeyStatus.derive Tests")
class ApiKeyStatusTest {

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    @Test
    @DisplayName("shouldReturnActive_whenNotRevokedAndNotExpired")
    void shouldReturnActive_whenNotRevokedAndNotExpired() {
        ApiKey key = new ApiKey();
        assertThat(ApiKeyStatus.derive(key, NOW)).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("shouldReturnActive_whenExpiresAtInFuture")
    void shouldReturnActive_whenExpiresAtInFuture() {
        ApiKey key = new ApiKey();
        key.setExpiresAt(NOW.plusSeconds(3600));
        assertThat(ApiKeyStatus.derive(key, NOW)).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("shouldReturnExpired_whenExpiresAtInPast")
    void shouldReturnExpired_whenExpiresAtInPast() {
        ApiKey key = new ApiKey();
        key.setExpiresAt(NOW.minusSeconds(1));
        assertThat(ApiKeyStatus.derive(key, NOW)).isEqualTo(ApiKeyStatus.EXPIRED);
    }

    @Test
    @DisplayName("shouldReturnExpired_whenExpiresAtEqualsNow")
    void shouldReturnExpired_whenExpiresAtEqualsNow() {
        ApiKey key = new ApiKey();
        key.setExpiresAt(NOW);
        assertThat(ApiKeyStatus.derive(key, NOW)).isEqualTo(ApiKeyStatus.EXPIRED);
    }

    @Test
    @DisplayName("shouldReturnRevoked_whenRevokedAtNotNull")
    void shouldReturnRevoked_whenRevokedAtNotNull() {
        ApiKey key = new ApiKey();
        key.revoke(NOW.minusSeconds(60));
        assertThat(ApiKeyStatus.derive(key, NOW)).isEqualTo(ApiKeyStatus.REVOKED);
    }

    @Test
    @DisplayName("shouldReturnRevoked_whenRevokedAndExpired_revocationTakesPriority")
    void shouldReturnRevoked_whenRevokedAndExpired_revocationTakesPriority() {
        ApiKey key = new ApiKey();
        key.setExpiresAt(NOW.minusSeconds(3600));
        key.revoke(NOW.minusSeconds(60));
        assertThat(ApiKeyStatus.derive(key, NOW)).isEqualTo(ApiKeyStatus.REVOKED);
    }
}
