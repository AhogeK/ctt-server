package com.ahogek.cttserver.auth.apikey.service;

import com.ahogek.cttserver.auth.apikey.dto.ApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.repository.ApiKeyRepository;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyQueryServiceImpl Tests")
class ApiKeyQueryServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID KEY_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");

    @Mock private ApiKeyRepository apiKeyRepository;

    @InjectMocks private ApiKeyQueryServiceImpl queryService;

    private ApiKey buildApiKey(String name, Instant createdAt) {
        User user = new User();
        user.setId(USER_ID);
        ApiKey key = new ApiKey();
        key.setId(KEY_ID);
        key.setUser(user);
        key.setName(name);
        key.setKeyPrefix("a1b2c3d4");
        key.setKeyHash("hash");
        key.setScopes(EnumSet.of(ApiKeyScope.READ));
        key.setCreatedAt(createdAt);
        return key;
    }

    @Nested
    @DisplayName("listApiKeys")
    class ListApiKeysTests {

        @Test
        @DisplayName("shouldReturnEmptyList_whenNoKeys")
        void shouldReturnEmptyList_whenNoKeys() {
            given(apiKeyRepository.findAllByUserId(USER_ID)).willReturn(List.of());
            List<ApiKeyResponse> result = queryService.listApiKeys(USER_ID);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("shouldReturnKeysOrderedByCreatedAtDesc")
        void shouldReturnKeysOrderedByCreatedAtDesc() {
            Instant earlier = Instant.parse("2026-07-01T10:00:00Z");
            Instant later = Instant.parse("2026-07-09T10:00:00Z");

            ApiKey key1 = buildApiKey("First", earlier);
            ApiKey key2 = buildApiKey("Second", later);

            given(apiKeyRepository.findAllByUserId(USER_ID)).willReturn(List.of(key1, key2));

            List<ApiKeyResponse> result = queryService.listApiKeys(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("Second");
            assertThat(result.get(1).name()).isEqualTo("First");
        }

        @Test
        @DisplayName("shouldMapToResponse_withoutExposingKeyHash")
        void shouldMapToResponse_withoutExposingKeyHash() {
            ApiKey key = buildApiKey("Test", Instant.now());
            given(apiKeyRepository.findAllByUserId(USER_ID)).willReturn(List.of(key));

            List<ApiKeyResponse> result = queryService.listApiKeys(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).keyPrefix()).isEqualTo("a1b2c3d4");
            assertThat(result.get(0).name()).isEqualTo("Test");
        }
    }

    @Nested
    @DisplayName("getApiKey")
    class GetApiKeyTests {

        @Test
        @DisplayName("shouldReturnKey_whenFoundAndOwnedByUser")
        void shouldReturnKey_whenFoundAndOwnedByUser() {
            ApiKey key = buildApiKey("Test", Instant.now());
            given(apiKeyRepository.findByIdAndUserId(KEY_ID, USER_ID)).willReturn(Optional.of(key));

            ApiKeyResponse result = queryService.getApiKey(USER_ID, KEY_ID);

            assertThat(result.id()).isEqualTo(KEY_ID);
            assertThat(result.name()).isEqualTo("Test");
        }

        @Test
        @DisplayName("shouldThrowNotFoundException_whenKeyNotFound")
        void shouldThrowNotFoundException_whenKeyNotFound() {
            given(apiKeyRepository.findByIdAndUserId(KEY_ID, USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> queryService.getApiKey(USER_ID, KEY_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
