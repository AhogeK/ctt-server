package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.auth.oauth.dto.OAuthAccountBinding;
import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthAccountQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-04-22T10:00:00Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-06-28T12:00:00Z");

    @Mock private UserOAuthAccountRepository oauthAccountRepository;

    @InjectMocks private OAuthAccountQueryService oauthAccountQueryService;

    private UserOAuthAccount stubGithubEntity(String login) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");

        UserOAuthAccount binding = new UserOAuthAccount();
        binding.setId(UUID.randomUUID());
        binding.setUser(user);
        binding.setProvider(OAuthProvider.GITHUB);
        binding.setProviderUserId("12345");
        binding.setProviderLogin(login);
        binding.setProviderEmail(login + "@example.com");
        binding.setAccessToken("encrypted-access-token-should-not-leak");
        binding.setRefreshToken("encrypted-refresh-token-should-not-leak");
        binding.setCreatedAt(CREATED_AT);
        binding.setUpdatedAt(UPDATED_AT);
        return binding;
    }

    private UserOAuthAccount stubGithubEntityWith(String providerLogin, String providerEmail) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");

        UserOAuthAccount binding = new UserOAuthAccount();
        binding.setId(UUID.randomUUID());
        binding.setUser(user);
        binding.setProvider(OAuthProvider.GITHUB);
        binding.setProviderUserId("12345");
        binding.setProviderLogin(providerLogin);
        binding.setProviderEmail(providerEmail);
        binding.setAccessToken("encrypted");
        binding.setRefreshToken("encrypted");
        binding.setCreatedAt(CREATED_AT);
        binding.setUpdatedAt(UPDATED_AT);
        return binding;
    }

    @Nested
    @DisplayName("listBindings")
    class ListBindings {

        @Test
        @DisplayName(
                "should return single GitHub binding with all required fields mapped correctly")
        void shouldReturnSingleBinding_whenUserHasOneGithubBinding() {
            UserOAuthAccount entity = stubGithubEntity("octocat");
            when(oauthAccountRepository.findAllByUserId(USER_ID)).thenReturn(List.of(entity));

            List<OAuthAccountBinding> result = oauthAccountQueryService.listBindings(USER_ID);

            assertThat(result).hasSize(1);
            OAuthAccountBinding binding = result.getFirst();
            assertThat(binding.provider()).isEqualTo(OAuthProvider.GITHUB.getValue());
            assertThat(binding.providerLogin()).isEqualTo("octocat");
            assertThat(binding.providerEmail()).isEqualTo("octocat@example.com");
            assertThat(binding.createdAt()).isEqualTo(CREATED_AT);
            assertThat(binding.updatedAt()).isEqualTo(UPDATED_AT);
        }

        @Test
        @DisplayName("should return all bindings preserving repository order")
        void shouldReturnAllBindings_whenUserHasMultipleBindings() {
            UserOAuthAccount first = stubGithubEntity("octocat");
            UserOAuthAccount second = stubGithubEntity("torvalds");
            when(oauthAccountRepository.findAllByUserId(USER_ID))
                    .thenReturn(List.of(first, second));

            List<OAuthAccountBinding> result = oauthAccountQueryService.listBindings(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).providerLogin()).isEqualTo("octocat");
            assertThat(result.get(1).providerLogin()).isEqualTo("torvalds");
        }

        @Test
        @DisplayName("should return empty list when user has no OAuth bindings")
        void shouldReturnEmptyList_whenUserHasNoBindings() {
            when(oauthAccountRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

            List<OAuthAccountBinding> result = oauthAccountQueryService.listBindings(USER_ID);

            assertThat(result).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Sensitive Field Protection")
    class SensitiveFieldProtection {

        @Test
        @DisplayName("should not declare accessToken, refreshToken, or providerUserId in record")
        void shouldNotExposeSensitiveFields_inRecordComponents() {
            assertThat(OAuthAccountBinding.class.getRecordComponents())
                    .extracting(RecordComponent::getName)
                    .doesNotContain("accessToken", "refreshToken", "providerUserId");
        }

        @Test
        @DisplayName("should map entity to DTO without leaking sensitive fields")
        void shouldMapEntityToDto_withoutLeakingSensitiveFields() {
            UserOAuthAccount entity = stubGithubEntity("octocat");

            OAuthAccountBinding dto = OAuthAccountBinding.fromEntity(entity);

            assertThat(dto)
                    .extracting(
                            OAuthAccountBinding::provider,
                            OAuthAccountBinding::providerLogin,
                            OAuthAccountBinding::providerEmail,
                            OAuthAccountBinding::createdAt,
                            OAuthAccountBinding::updatedAt)
                    .containsExactly(
                            OAuthProvider.GITHUB.getValue(),
                            "octocat",
                            "octocat@example.com",
                            CREATED_AT,
                            UPDATED_AT);
        }
    }

    @Nested
    @DisplayName("Fallback Chain")
    class FallbackChain {

        @Test
        @DisplayName("Should fall back to email local-part when providerLogin is null")
        void shouldFallbackToEmailLocalPart_whenProviderLoginIsNull() {
            UserOAuthAccount entity = stubGithubEntityWith(null, "torvalds@example.com");

            OAuthAccountBinding dto = OAuthAccountBinding.fromEntity(entity);

            assertThat(dto.providerLogin()).isEqualTo("torvalds");
            assertThat(dto.providerEmail()).isEqualTo("torvalds@example.com");
        }

        @Test
        @DisplayName(
                "Should fall back to providerUserId when both providerLogin and providerEmail are null")
        void shouldFallbackToProviderUserId_whenBothLoginAndEmailAreNull() {
            UserOAuthAccount entity = stubGithubEntityWith(null, null);

            OAuthAccountBinding dto = OAuthAccountBinding.fromEntity(entity);

            assertThat(dto.providerLogin()).isEqualTo("12345");
            assertThat(dto.providerEmail()).isNull();
        }

        @Test
        @DisplayName("Should fall back to providerUserId when email exists but lacks '@' delimiter")
        void shouldFallbackToProviderUserId_whenEmailLacksAtDelimiter() {
            UserOAuthAccount entity = stubGithubEntityWith(null, "malformed-email");

            OAuthAccountBinding dto = OAuthAccountBinding.fromEntity(entity);

            assertThat(dto.providerLogin()).isEqualTo("12345");
        }

        @Test
        @DisplayName("Should trim whitespace from providerLogin when present")
        void shouldTrimWhitespaceFromProviderLogin() {
            UserOAuthAccount entity = stubGithubEntityWith("  octocat  ", "octocat@example.com");

            OAuthAccountBinding dto = OAuthAccountBinding.fromEntity(entity);

            assertThat(dto.providerLogin()).isEqualTo("octocat");
        }

        @Test
        @DisplayName("Should fall back to providerUserId when email local-part is empty")
        void shouldFallbackToProviderUserId_whenEmailLocalPartIsEmpty() {
            UserOAuthAccount entity = stubGithubEntityWith(null, "@example.com");

            OAuthAccountBinding dto = OAuthAccountBinding.fromEntity(entity);

            assertThat(dto.providerLogin()).isEqualTo("12345");
        }
    }
}
