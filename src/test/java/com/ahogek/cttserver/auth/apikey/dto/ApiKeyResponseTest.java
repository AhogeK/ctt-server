package com.ahogek.cttserver.auth.apikey.dto;

import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyStatus;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKeyResponse.fromEntity Tests")
class ApiKeyResponseTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID KEY_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");

    private ApiKey buildEntity() {
        User user = new User();
        user.setId(USER_ID);
        ApiKey key = new ApiKey();
        key.setId(KEY_ID);
        key.setUser(user);
        key.setName("Test Key");
        key.setKeyPrefix("a1b2c3d4");
        key.setKeyHash("sha256hash");
        key.setScopes(EnumSet.of(ApiKeyScope.READ, ApiKeyScope.SYNC));
        key.setCreatedAt(Instant.parse("2026-07-09T10:00:00Z"));
        return key;
    }

    @Test
    @DisplayName("shouldMapAllFields_fromEntity")
    void shouldMapAllFields_fromEntity() {
        ApiKey entity = buildEntity();
        ApiKeyResponse response = ApiKeyResponse.fromEntity(entity);

        assertThat(response.id()).isEqualTo(KEY_ID);
        assertThat(response.name()).isEqualTo("Test Key");
        assertThat(response.keyPrefix()).isEqualTo("a1b2c3d4");
        assertThat(response.scopes()).containsExactly(ApiKeyScope.READ, ApiKeyScope.SYNC);
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-09T10:00:00Z"));
        assertThat(response.status()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("shouldNotExposeKeyHash")
    void shouldNotExposeKeyHash() {
        ApiKey entity = buildEntity();
        ApiKeyResponse response = ApiKeyResponse.fromEntity(entity);

        assertThat(response.toString()).doesNotContain("sha256hash");
    }

    @Test
    @DisplayName("shouldDeriveRevokedStatus_whenRevokedAtNotNull")
    void shouldDeriveRevokedStatus_whenRevokedAtNotNull() {
        ApiKey entity = buildEntity();
        entity.revoke(Instant.now());
        ApiKeyResponse response = ApiKeyResponse.fromEntity(entity);

        assertThat(response.status()).isEqualTo(ApiKeyStatus.REVOKED);
    }

    @Test
    @DisplayName("shouldIncludeNullTimestamps_whenNeverUsed")
    void shouldIncludeNullTimestamps_whenNeverUsed() {
        ApiKey entity = buildEntity();
        ApiKeyResponse response = ApiKeyResponse.fromEntity(entity);

        assertThat(response.lastUsedAt()).isNull();
        assertThat(response.expiresAt()).isNull();
        assertThat(response.revokedAt()).isNull();
    }
}
