package com.ahogek.cttserver.auth.apikey.entity;

import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKey Entity Behavior Tests")
class ApiKeyTest {

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    @Nested
    @DisplayName("isActive")
    class IsActiveTests {

        @Test
        @DisplayName("shouldReturnTrue_whenNotRevokedAndNotExpired")
        void shouldReturnTrue_whenNotRevokedAndNotExpired() {
            ApiKey key = new ApiKey();
            assertThat(key.isActive(NOW)).isTrue();
        }

        @Test
        @DisplayName("shouldReturnFalse_whenRevoked")
        void shouldReturnFalse_whenRevoked() {
            ApiKey key = new ApiKey();
            key.revoke(NOW.minusSeconds(60));
            assertThat(key.isActive(NOW)).isFalse();
        }

        @Test
        @DisplayName("shouldReturnFalse_whenExpired")
        void shouldReturnFalse_whenExpired() {
            ApiKey key = new ApiKey();
            key.setExpiresAt(NOW.minusSeconds(1));
            assertThat(key.isActive(NOW)).isFalse();
        }

        @Test
        @DisplayName("shouldReturnTrue_whenExpiresAtInFuture")
        void shouldReturnTrue_whenExpiresAtInFuture() {
            ApiKey key = new ApiKey();
            key.setExpiresAt(NOW.plusSeconds(3600));
            assertThat(key.isActive(NOW)).isTrue();
        }
    }

    @Nested
    @DisplayName("revoke")
    class RevokeTests {

        @Test
        @DisplayName("shouldSetRevokedAt_whenNotPreviouslyRevoked")
        void shouldSetRevokedAt_whenNotPreviouslyRevoked() {
            ApiKey key = new ApiKey();
            key.revoke(NOW);
            assertThat(key.getRevokedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("shouldPreserveOriginalRevokedAt_whenAlreadyRevoked_idempotent")
        void shouldPreserveOriginalRevokedAt_whenAlreadyRevoked_idempotent() {
            Instant firstRevoke = NOW.minusSeconds(3600);
            ApiKey key = new ApiKey();
            key.revoke(firstRevoke);
            key.revoke(NOW);
            assertThat(key.getRevokedAt()).isEqualTo(firstRevoke);
        }
    }

    @Nested
    @DisplayName("setScopes")
    class SetScopesTests {

        @Test
        @DisplayName("shouldNormalizeNull_toEmptyEnumSet")
        void shouldNormalizeNull_toEmptyEnumSet() {
            ApiKey key = new ApiKey();
            key.setScopes(null);
            assertThat(key.getScopes()).isEmpty();
            assertThat(key.getScopes()).isInstanceOf(EnumSet.class);
        }

        @Test
        @DisplayName("shouldPreserveProvidedScopes")
        void shouldPreserveProvidedScopes() {
            ApiKey key = new ApiKey();
            key.setScopes(EnumSet.of(ApiKeyScope.READ, ApiKeyScope.SYNC));
            assertThat(key.getScopes()).containsExactly(ApiKeyScope.READ, ApiKeyScope.SYNC);
        }
    }

    @Nested
    @DisplayName("touchLastUsed")
    class TouchLastUsedTests {

        @Test
        @DisplayName("shouldUpdateLastUsedAt")
        void shouldUpdateLastUsedAt() {
            ApiKey key = new ApiKey();
            key.touchLastUsed(NOW);
            assertThat(key.getLastUsedAt()).isEqualTo(NOW);
        }
    }
}
