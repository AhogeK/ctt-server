package com.ahogek.cttserver.auth.oauth.controller;

import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.oauth.client.GitHubOAuthClient;
import com.ahogek.cttserver.auth.oauth.model.GitHubTokenResponse;
import com.ahogek.cttserver.auth.oauth.model.GitHubUserInfo;
import com.ahogek.cttserver.auth.oauth.model.OAuthStatePayload;
import com.ahogek.cttserver.auth.oauth.service.OAuthLoginOrRegisterService;
import com.ahogek.cttserver.auth.oauth.service.OAuthStateService;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.OAuthProperties;
import com.ahogek.cttserver.common.config.properties.SecurityProperties.OAuthProperties.GitHubProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;

import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * OAuthCallbackController HTTP business logic tests.
 *
 * <p>Covers authorize and callback endpoints using MockMvc slice testing with mocked
 * dependencies.
 */
@BaseControllerSliceTest(
        value = OAuthCallbackController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {RateLimitAspect.class, IdempotentAspect.class, TermsCheckFilter.class}))
@DisplayName("OAuthCallbackController MockMvc Tests")
class OAuthCallbackControllerMockMvcTest {

    private static final String FRONTEND_URL = "https://ctt.example.com";

    @Autowired private MockMvcTester mvc;

    @MockitoBean private OAuthStateService stateService;
    @MockitoBean private GitHubOAuthClient githubClient;
    @MockitoBean private OAuthLoginOrRegisterService loginOrRegisterService;
    @MockitoBean private SecurityProperties securityProps;

    @BeforeEach
    void setUpSecurityProperties() {
        GitHubProperties githubProps =
                new GitHubProperties(
                        "test-client-id",
                        "test-client-secret",
                        "https://github.com/login/oauth/access_token",
                        "https://api.github.com/user",
                        "https://api.github.com/user/emails",
                        "read:user,user:email");
        OAuthProperties oauthProps = new OAuthProperties(FRONTEND_URL, null, githubProps);
        BDDMockito.given(securityProps.oauth()).willReturn(oauthProps);
    }

    @Nested
    @DisplayName("GET /api/v1/auth/oauth/{provider}/authorize")
    @WithMockUser
    class AuthorizeTests {

        @Test
        @DisplayName("Should return 200 OK with GitHub authorization URL")
        void shouldReturnAuthUrl_whenProviderIsGitHub() {
            String stateId = UUID.randomUUID().toString();
            BDDMockito.given(stateService.generateAndSaveState(any(OAuthStatePayload.class)))
                    .willReturn(stateId);

            assertThat(mvc.get().uri("/api/v1/auth/oauth/{provider}/authorize", "github"))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data.authUrl")
                    .asString()
                    .contains("https://github.com/login/oauth/authorize")
                    .contains("client_id=test-client-id")
                    .contains("scope=read:user,user:email")
                    .contains("state=" + stateId);
        }

        @Test
        @DisplayName("Should redirect to error page when provider is unsupported")
        void shouldRedirectToError_whenProviderIsUnsupported() {
            assertThat(mvc.get().uri("/api/v1/auth/oauth/{provider}/authorize", "google"))
                    .hasStatus(302)
                    .redirectedUrl()
                    .contains("code=OAUTH_INTERNAL_ERROR");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/oauth/{provider}/callback")
    @WithMockUser
    class CallbackTests {

        private static final String STATE = UUID.randomUUID().toString();
        private static final String CODE = "test-auth-code";
        private static final String ACCESS_TOKEN = "gho_test-access-token";
        private static final String REFRESH_TOKEN = "test-refresh-token";
        private static final UUID USER_ID = UUID.randomUUID();

        private void stubSuccessfulCallbackChain() {
            OAuthStatePayload payload =
                    new OAuthStatePayload(OAuthStatePayload.Action.LOGIN, null, null);
            BDDMockito.given(stateService.consumeState(STATE)).willReturn(payload);

            GitHubTokenResponse tokenResponse =
                    new GitHubTokenResponse(ACCESS_TOKEN, "bearer", "read:user,user:email");
            BDDMockito.given(githubClient.exchangeCodeForToken(CODE, STATE))
                    .willReturn(tokenResponse);

            GitHubUserInfo userInfo =
                    new GitHubUserInfo(
                            12345L, "octocat", "The Octocat", null, "octocat@example.com");
            BDDMockito.given(githubClient.getUserInfo(ACCESS_TOKEN)).willReturn(userInfo);

            LoginResponse loginResponse =
                    new LoginResponse(USER_ID, ACCESS_TOKEN, REFRESH_TOKEN, 3600L);
            BDDMockito.given(
                            loginOrRegisterService.process(any(), eq(ACCESS_TOKEN), eq(userInfo)))
                    .willReturn(loginResponse);
        }

        @Test
        @DisplayName("Should redirect to frontend with tokens on successful callback")
        void shouldRedirectWithTokens_whenCallbackIsValid() {
            stubSuccessfulCallbackChain();

            assertThat(
                            mvc.get()
                                    .uri(
                                            "/api/v1/auth/oauth/github/callback?code=%s&state=%s"
                                                    .formatted(CODE, STATE)))
                    .hasStatus(302)
                    .redirectedUrl()
                    .contains(FRONTEND_URL + "/oauth/callback")
                    .contains("accessToken=" + ACCESS_TOKEN)
                    .contains("refreshToken=" + REFRESH_TOKEN)
                    .contains("termsExpired=false");
        }

        @Test
        @DisplayName("Should redirect to error page when state is missing")
        void shouldRedirectToError_whenStateIsMissing() {
            assertThat(
                            mvc.get()
                                    .uri(
                                            "/api/v1/auth/oauth/github/callback?code=%s"
                                                    .formatted(CODE)))
                    .hasStatus(302)
                    .redirectedUrl()
                    .contains("code=MISSING_OAUTH_PARAMS");
        }

        @Test
        @DisplayName("Should redirect to error page when code is missing")
        void shouldRedirectToError_whenCodeIsMissing() {
            assertThat(
                            mvc.get()
                                    .uri(
                                            "/api/v1/auth/oauth/github/callback?state=%s"
                                                    .formatted(STATE)))
                    .hasStatus(302)
                    .redirectedUrl()
                    .contains("code=MISSING_OAUTH_PARAMS");
        }

        @Test
        @DisplayName("Should redirect to error page when state is expired or invalid")
        void shouldRedirectToError_whenStateIsExpired() {
            BDDMockito.given(stateService.consumeState(STATE))
                    .willThrow(new ForbiddenException(ErrorCode.AUTH_013));

            assertThat(
                            mvc.get()
                                    .uri(
                                            "/api/v1/auth/oauth/github/callback?code=%s&state=%s"
                                                    .formatted(CODE, STATE)))
                    .hasStatus(302)
                    .redirectedUrl()
                    .contains("code=AUTH_013");
        }

        @Test
        @DisplayName("Should redirect to error page when provider returns an error")
        void shouldRedirectToError_whenProviderReturnsError() {
            assertThat(
                            mvc.get()
                                    .uri(
                                            "/api/v1/auth/oauth/github/callback"
                                                    + "?error=access_denied"
                                                    + "&error_description=The+user+denied+the+application."))
                    .hasStatus(302)
                    .redirectedUrl()
                    .contains("code=OAUTH_PROVIDER_ERROR");
        }
    }
}
