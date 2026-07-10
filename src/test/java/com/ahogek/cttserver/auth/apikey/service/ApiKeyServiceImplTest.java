package com.ahogek.cttserver.auth.apikey.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.crypto.ApiKeyHasher;
import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyRequest;
import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.repository.ApiKeyRepository;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyServiceImpl Tests")
class ApiKeyServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID KEY_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");
    private static final String RAW_KEY =
            "cttak_a1b2c3d4_z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4";
    private static final String KEY_HASH = "abc123def456";
    private static final String KEY_PREFIX = "a1b2c3d4";

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApiKeyHasher apiKeyHasher;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private ApiKeyServiceImpl apiKeyService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);
    }

    @Nested
    @DisplayName("createApiKey")
    class CreateApiKeyTests {

        @Test
        @DisplayName("shouldCreateApiKey_whenValidRequest")
        void shouldCreateApiKey_whenValidRequest() {
            // Given
            CreateApiKeyRequest request =
                    new CreateApiKeyRequest(
                            "My Key", EnumSet.of(ApiKeyScope.READ, ApiKeyScope.SYNC), null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(apiKeyRepository.countByUserIdAndRevokedAtIsNull(USER_ID)).willReturn(0L);
            given(apiKeyHasher.generateRawKey()).willReturn(RAW_KEY);
            given(apiKeyHasher.hashKey(RAW_KEY)).willReturn(KEY_HASH);
            given(apiKeyRepository.save(any(ApiKey.class)))
                    .willAnswer(
                            invocation -> {
                                ApiKey entity = invocation.getArgument(0);
                                entity.setId(KEY_ID);
                                entity.setCreatedAt(Instant.now());
                                return entity;
                            });

            // When
            CreateApiKeyResponse response = apiKeyService.createApiKey(USER_ID, request);

            // Then
            assertThat(response.rawKey()).isEqualTo(RAW_KEY);
            assertThat(response.apiKey().id()).isEqualTo(KEY_ID);
            assertThat(response.apiKey().name()).isEqualTo("My Key");

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            then(apiKeyRepository).should().save(captor.capture());
            ApiKey saved = captor.getValue();
            assertThat(saved.getKeyHash()).isEqualTo(KEY_HASH);
            assertThat(saved.getKeyPrefix()).isEqualTo(KEY_PREFIX);
            assertThat(saved.getUser()).isEqualTo(user);

            then(auditLogService)
                    .should()
                    .logSuccess(
                            USER_ID,
                            AuditAction.API_KEY_CREATED,
                            ResourceType.API_KEY,
                            KEY_ID.toString());
        }

        @Test
        @DisplayName("shouldThrowNotFoundException_whenUserNotFound")
        void shouldThrowNotFoundException_whenUserNotFound() {
            // Given
            CreateApiKeyRequest request =
                    new CreateApiKeyRequest("Key", Set.of(ApiKeyScope.READ), null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> apiKeyService.createApiKey(USER_ID, request))
                    .isInstanceOf(NotFoundException.class);
            then(apiKeyRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("shouldThrowConflictException_whenLimitExceeded")
        void shouldThrowConflictException_whenLimitExceeded() {
            // Given
            CreateApiKeyRequest request =
                    new CreateApiKeyRequest("Key", Set.of(ApiKeyScope.READ), null);
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(apiKeyRepository.countByUserIdAndRevokedAtIsNull(USER_ID))
                    .willReturn((long) ApiKeyServiceImpl.MAX_KEYS_PER_USER);

            // When & Then
            assertThatThrownBy(() -> apiKeyService.createApiKey(USER_ID, request))
                    .isInstanceOf(ConflictException.class);
            then(apiKeyRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeApiKey")
    class RevokeApiKeyTests {

        @Test
        @DisplayName("shouldRevokeApiKey_whenKeyExistsAndNotRevoked")
        void shouldRevokeApiKey_whenKeyExistsAndNotRevoked() {
            // Given
            ApiKey apiKey = new ApiKey();
            apiKey.setId(KEY_ID);
            apiKey.setUser(user);
            given(apiKeyRepository.findByIdAndUserId(KEY_ID, USER_ID))
                    .willReturn(Optional.of(apiKey));
            given(apiKeyRepository.save(any(ApiKey.class))).willReturn(apiKey);

            // When
            apiKeyService.revokeApiKey(USER_ID, KEY_ID);

            // Then
            assertThat(apiKey.getRevokedAt()).isNotNull();
            then(apiKeyRepository).should().save(apiKey);
        }

        @Test
        @DisplayName("shouldNotDoubleAudit_whenKeyAlreadyRevoked")
        void shouldNotDoubleAudit_whenKeyAlreadyRevoked() {
            // Given
            ApiKey apiKey = new ApiKey();
            apiKey.setId(KEY_ID);
            apiKey.setUser(user);
            apiKey.revoke(Instant.now().minusSeconds(60));
            given(apiKeyRepository.findByIdAndUserId(KEY_ID, USER_ID))
                    .willReturn(Optional.of(apiKey));

            // When
            apiKeyService.revokeApiKey(USER_ID, KEY_ID);

            // Then
            then(apiKeyRepository).should(never()).save(any());
            then(auditLogService).should(never()).logSuccess(any(), any(), any(), any());
        }

        @Test
        @DisplayName("shouldThrowNotFoundException_whenKeyNotFound")
        void shouldThrowNotFoundException_whenKeyNotFound() {
            // Given
            given(apiKeyRepository.findByIdAndUserId(KEY_ID, USER_ID)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> apiKeyService.revokeApiKey(USER_ID, KEY_ID))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("shouldThrowNotFoundException_whenKeyBelongsToOtherUser")
        void shouldThrowNotFoundException_whenKeyBelongsToOtherUser() {
            // Given
            given(apiKeyRepository.findByIdAndUserId(KEY_ID, USER_ID)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> apiKeyService.revokeApiKey(USER_ID, KEY_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("validateAndTouch")
    class ValidateAndTouchTests {

        @Test
        @DisplayName("shouldReturnApiKey_whenValidAndActive")
        void shouldReturnApiKey_whenValidAndActive() {
            // Given
            ApiKey apiKey = new ApiKey();
            apiKey.setId(KEY_ID);
            apiKey.setUser(user);
            apiKey.setKeyHash(KEY_HASH);
            given(apiKeyHasher.hashKey(RAW_KEY)).willReturn(KEY_HASH);
            given(apiKeyRepository.findByKeyHash(KEY_HASH)).willReturn(Optional.of(apiKey));
            given(apiKeyRepository.save(any(ApiKey.class))).willReturn(apiKey);

            // When
            ApiKey result = apiKeyService.validateAndTouch(RAW_KEY);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(KEY_ID);
            assertThat(result.getLastUsedAt()).isNotNull();
            then(apiKeyRepository).should().save(apiKey);
        }

        @Test
        @DisplayName("shouldThrowNotFoundException_whenKeyHashNotFound")
        void shouldThrowNotFoundException_whenKeyHashNotFound() {
            // Given
            given(apiKeyHasher.hashKey(RAW_KEY)).willReturn(KEY_HASH);
            given(apiKeyRepository.findByKeyHash(KEY_HASH)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> apiKeyService.validateAndTouch(RAW_KEY))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("shouldThrowForbiddenException_whenKeyRevoked")
        void shouldThrowForbiddenException_whenKeyRevoked() {
            // Given
            ApiKey apiKey = new ApiKey();
            apiKey.setId(KEY_ID);
            apiKey.setUser(user);
            apiKey.revoke(Instant.now().minusSeconds(60));
            given(apiKeyHasher.hashKey(RAW_KEY)).willReturn(KEY_HASH);
            given(apiKeyRepository.findByKeyHash(KEY_HASH)).willReturn(Optional.of(apiKey));

            // When & Then
            assertThatThrownBy(() -> apiKeyService.validateAndTouch(RAW_KEY))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("shouldThrowUnauthorizedException_whenKeyExpired")
        void shouldThrowUnauthorizedException_whenKeyExpired() {
            // Given
            ApiKey apiKey = new ApiKey();
            apiKey.setId(KEY_ID);
            apiKey.setUser(user);
            apiKey.setExpiresAt(Instant.now().minusSeconds(60));
            given(apiKeyHasher.hashKey(RAW_KEY)).willReturn(KEY_HASH);
            given(apiKeyRepository.findByKeyHash(KEY_HASH)).willReturn(Optional.of(apiKey));

            // When & Then
            assertThatThrownBy(() -> apiKeyService.validateAndTouch(RAW_KEY))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("shouldTouchLastUsedAt_whenValidKey")
        void shouldTouchLastUsedAt_whenValidKey() {
            // Given
            ApiKey apiKey = new ApiKey();
            apiKey.setId(KEY_ID);
            apiKey.setUser(user);
            given(apiKeyHasher.hashKey(RAW_KEY)).willReturn(KEY_HASH);
            given(apiKeyRepository.findByKeyHash(KEY_HASH)).willReturn(Optional.of(apiKey));
            given(apiKeyRepository.save(any(ApiKey.class))).willReturn(apiKey);

            // When
            apiKeyService.validateAndTouch(RAW_KEY);

            // Then
            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            then(apiKeyRepository).should().save(captor.capture());
            assertThat(captor.getValue().getLastUsedAt()).isNotNull();
        }

        @ParameterizedTest
        @EnumSource(
                value = UserStatus.class,
                names = {"LOCKED", "SUSPENDED", "DELETED", "PENDING_VERIFICATION"})
        @DisplayName("shouldThrowForbiddenException_whenUserStatusNotActive")
        void shouldThrowForbiddenException_whenUserStatusNotActive(UserStatus status) {
            // Given
            ReflectionTestUtils.setField(user, "status", status);
            ApiKey apiKey = new ApiKey();
            apiKey.setId(KEY_ID);
            apiKey.setUser(user);
            given(apiKeyHasher.hashKey(RAW_KEY)).willReturn(KEY_HASH);
            given(apiKeyRepository.findByKeyHash(KEY_HASH)).willReturn(Optional.of(apiKey));

            // When & Then
            assertThatThrownBy(() -> apiKeyService.validateAndTouch(RAW_KEY))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(ex -> ((ForbiddenException) ex).errorCode())
                    .isEqualTo(
                            switch (status) {
                                case LOCKED -> ErrorCode.AUTH_004;
                                case SUSPENDED -> ErrorCode.AUTH_005;
                                case PENDING_VERIFICATION -> ErrorCode.AUTH_006;
                                case DELETED -> ErrorCode.AUTH_022;
                                default -> ErrorCode.AUTH_022;
                            });
        }
    }
}
