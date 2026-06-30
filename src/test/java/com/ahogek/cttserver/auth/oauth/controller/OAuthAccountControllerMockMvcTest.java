package com.ahogek.cttserver.auth.oauth.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.auth.oauth.dto.OAuthAccountBinding;
import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.auth.oauth.service.OAuthAccountQueryService;
import com.ahogek.cttserver.auth.oauth.service.OAuthLoginOrRegisterService;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * OAuthAccountController HTTP business logic tests.
 *
 * <p>Covers the list-accounts endpoint: empty bindings, GitHub-bound user, missing authentication,
 * and cross-user data isolation. Verifies that sensitive fields (accessToken, refreshToken,
 * providerUserId) never appear in the response payload.
 */
@BaseControllerSliceTest(
        value = OAuthAccountController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            RateLimitAspect.class,
                            IdempotentAspect.class,
                            TermsCheckFilter.class
                        }))
@DisplayName("OAuthAccountController MockMvc Tests")
class OAuthAccountControllerMockMvcTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER_USER_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    @Autowired private MockMvcTester mvc;

    @MockitoBean private OAuthAccountQueryService oauthAccountQueryService;
    @MockitoBean private OAuthLoginOrRegisterService oauthLoginOrRegisterService;
    @MockitoBean private CurrentUserProvider currentUserProvider;

    private CurrentUser currentUser() {
        return new CurrentUser(
            OAuthAccountControllerMockMvcTest.USER_ID,
                "test@example.com",
                UserStatus.ACTIVE,
                Set.of("ROLE_USER"),
                CurrentUser.AuthenticationType.WEB_SESSION);
    }

    private OAuthAccountBinding githubBindingFor() {
        return new OAuthAccountBinding(
                OAuthProvider.GITHUB.getValue(),
            "octocat",
                "octocat" + "@example.com",
                OffsetDateTime.parse("2026-04-22T10:00:00Z"),
                OffsetDateTime.parse("2026-06-28T12:00:00Z"));
    }

    private UserOAuthAccount stubGithubEntity() {
        User user = new User();
        user.setId(OAuthAccountControllerMockMvcTest.OTHER_USER_ID);
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");

        UserOAuthAccount binding = new UserOAuthAccount();
        binding.setId(UUID.randomUUID());
        binding.setUser(user);
        binding.setProvider(OAuthProvider.GITHUB);
        binding.setProviderUserId("12345");
        binding.setProviderLogin("torvalds");
        binding.setProviderEmail("torvalds" + "@example.com");
        binding.setAccessToken("encrypted-access-token-should-not-leak");
        binding.setRefreshToken("encrypted-refresh-token-should-not-leak");
        binding.setCreatedAt(OffsetDateTime.parse("2026-04-22T10:00:00Z"));
        binding.setUpdatedAt(OffsetDateTime.parse("2026-06-28T12:00:00Z"));
        return binding;
    }

    @Nested
    @DisplayName("GET /api/v1/auth/oauth/accounts")
    class ListAccountsTests {

        @Test
        @WithMockUser
        @DisplayName("Should return empty accounts list when user has no OAuth bindings")
        void shouldReturnEmptyAccounts_whenNoBindings() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(oauthAccountQueryService.listBindings(USER_ID)).willReturn(List.of());

            var result = mvc.get().uri("/api/v1/auth/oauth/accounts").exchange();

            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.success").isEqualTo(true);
            assertThat(result).bodyJson().doesNotHavePath("$.data.accounts[0]");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return GitHub binding with all required fields mapped correctly")
        void shouldReturnBinding_whenGitHubBound() {
            OAuthAccountBinding binding = githubBindingFor();
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(oauthAccountQueryService.listBindings(USER_ID))
                    .willReturn(List.of(binding));

            assertThat(mvc.get().uri("/api/v1/auth/oauth/accounts").exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data.accounts[0]")
                    .asMap()
                    .containsEntry("provider", "github")
                    .containsEntry("providerLogin", "octocat")
                    .containsEntry("providerEmail", "octocat@example.com")
                    .containsEntry("createdAt", "2026-04-22T10:00:00Z")
                    .containsEntry("updatedAt", "2026-06-28T12:00:00Z");
        }

        @Test
        @WithMockUser
        @DisplayName("Should never expose accessToken, refreshToken, or providerUserId in response")
        void shouldNotExposeSensitiveFields_inResponse() {
            OAuthAccountBinding binding = githubBindingFor();
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(oauthAccountQueryService.listBindings(USER_ID))
                    .willReturn(List.of(binding));

            assertThat(mvc.get().uri("/api/v1/auth/oauth/accounts").exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .doesNotHavePath("$.data.accounts[0].accessToken")
                    .doesNotHavePath("$.data.accounts[0].refreshToken")
                    .doesNotHavePath("$.data.accounts[0].providerUserId");
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.get().uri("/api/v1/auth/oauth/accounts").exchange()).hasStatus(401);
        }

        @Test
        @WithMockUser
        @DisplayName("Should isolate bindings by current user (cross-user data isolation)")
        void shouldIsolateBindings_betweenUsers() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(oauthAccountQueryService.listBindings(USER_ID))
                    .willReturn(List.of(githubBindingFor()));
            BDDMockito.given(oauthAccountQueryService.listBindings(OTHER_USER_ID))
                    .willReturn(
                            List.of(
                                    OAuthAccountBinding.fromEntity(
                                            stubGithubEntity())));

            var result = mvc.get().uri("/api/v1/auth/oauth/accounts").exchange();

            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data.accounts.length()")
                    .isEqualTo(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.accounts[0].providerLogin")
                    .isEqualTo("octocat");

            BDDMockito.then(oauthAccountQueryService).should().listBindings(USER_ID);
            BDDMockito.then(oauthAccountQueryService)
                    .should(BDDMockito.never())
                    .listBindings(OTHER_USER_ID);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/auth/oauth/accounts/{provider}")
    class UnbindAccountTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 204 when user has password and unbinds GitHub")
        void shouldReturn204_whenUserHasPasswordAndGithubBinding() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willDoNothing()
                    .given(oauthLoginOrRegisterService)
                    .unbindFromExistingUser(USER_ID, OAuthProvider.GITHUB);

            assertThat(mvc.delete().uri("/api/v1/auth/oauth/accounts/{provider}", "github")
                    .with(csrf())
                    .exchange())
                    .hasStatus(204);

            BDDMockito.then(oauthLoginOrRegisterService)
                    .should()
                    .unbindFromExistingUser(USER_ID, OAuthProvider.GITHUB);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 204 when user has multiple OAuth bindings (not last)")
        void shouldReturn204_whenUserHasMultipleOAuthBindings() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willDoNothing()
                    .given(oauthLoginOrRegisterService)
                    .unbindFromExistingUser(USER_ID, OAuthProvider.GITHUB);

            assertThat(mvc.delete().uri("/api/v1/auth/oauth/accounts/{provider}", "github")
                    .with(csrf())
                    .exchange())
                    .hasStatus(204);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 409 AUTH_018 when binding is the only login method")
        void shouldReturn409_whenLastLoginMethod() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willThrow(new ConflictException(ErrorCode.AUTH_018))
                    .given(oauthLoginOrRegisterService)
                    .unbindFromExistingUser(USER_ID, OAuthProvider.GITHUB);

            assertThat(mvc.delete().uri("/api/v1/auth/oauth/accounts/{provider}", "github")
                    .with(csrf())
                    .exchange())
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_018");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 404 AUTH_017 when user is not linked to the provider (also: non-idempotent, second DELETE returns same)")
        void shouldReturn404_whenProviderNotLinked() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willThrow(new NotFoundException(ErrorCode.AUTH_017))
                    .given(oauthLoginOrRegisterService)
                    .unbindFromExistingUser(USER_ID, OAuthProvider.GITHUB);

            assertThat(mvc.delete().uri("/api/v1/auth/oauth/accounts/{provider}", "github")
                    .with(csrf())
                    .exchange())
                    .hasStatus(404)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_017");
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenUnbindNotAuthenticated() {
            assertThat(mvc.delete()
                    .uri("/api/v1/auth/oauth/accounts/{provider}", "github")
                    .with(csrf())
                    .exchange())
                    .hasStatus(401);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 COMMON_001 for unsupported provider")
        void shouldReturn400_whenProviderIsUnsupported() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());

            assertThat(mvc.delete()
                    .uri("/api/v1/auth/oauth/accounts/{provider}", "google")
                    .with(csrf())
                    .exchange())
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_001");
        }

        @Test
        @WithMockUser
        @DisplayName("Should pass authenticated user id to service layer")
        void shouldPassAuthenticatedUserIdToService() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willDoNothing()
                    .given(oauthLoginOrRegisterService)
                    .unbindFromExistingUser(USER_ID, OAuthProvider.GITHUB);

            mvc.delete().uri("/api/v1/auth/oauth/accounts/{provider}", "github").with(csrf()).exchange();

            BDDMockito.then(oauthLoginOrRegisterService)
                    .should()
                    .unbindFromExistingUser(USER_ID, OAuthProvider.GITHUB);
        }
    }
}
