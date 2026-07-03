package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.auth.oauth.model.GitHubUserInfo;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.auth.service.JwtTokenProvider;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthLoginOrRegisterServiceTermsTest {

    private static final String TEST_EMAIL = "oauth@example.com";
    private static final long GITHUB_USER_ID = 12345678L;
    private static final String GITHUB_LOGIN = "octocat";
    private static final String GITHUB_NAME = "The Octocat";
    private static final String GITHUB_AVATAR_URL =
            "https://avatars.githubusercontent.com/u/12345678?v=4";
    private static final String TEST_GITHUB_ACCESS_TOKEN = "gho_test_token_xxxxxxxx";
    private static final String TEST_CTT_ACCESS_TOKEN = "test.access.token";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final String CURRENT_TERMS_VERSION = "1.0.0";

    @Mock private UserOAuthAccountRepository oauthAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityProperties.JwtProperties jwtProps;

    private final TermsProperties termsProperties =
            new TermsProperties(CURRENT_TERMS_VERSION, null);

    private OAuthLoginOrRegisterService oauthLoginService;

    @BeforeEach
    void setUp() {
        when(securityProperties.jwt()).thenReturn(jwtProps);
        when(jwtProps.accessTokenTtl()).thenReturn(ACCESS_TOKEN_TTL);
        when(jwtProps.refreshTokenTtlWeb()).thenReturn(REFRESH_TOKEN_TTL);

        oauthLoginService =
                new OAuthLoginOrRegisterService(
                        oauthAccountRepository,
                        userRepository,
                        jwtTokenProvider,
                        refreshTokenRepository,
                        auditLogService,
                        securityProperties,
                        termsProperties);

        doAnswer(
                        invocation -> {
                            UserOAuthAccount account = invocation.getArgument(0);
                            if (account.getId() == null) {
                                ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
                            }
                            return account;
                        })
                .when(oauthAccountRepository)
                .save(any());

        doAnswer(
                        invocation -> {
                            User user = invocation.getArgument(0);
                            if (user.getId() == null) {
                                ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
                            }
                            return user;
                        })
                .when(userRepository)
                .save(any());
    }

    @Test
    @DisplayName("should set termsVersion to current version when registering new OAuth user")
    void shouldSetTermsVersion_whenRegisteringNewOAuthUser() {
        when(oauthAccountRepository.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());
        when(jwtTokenProvider.generateAccessToken(any(User.class)))
                .thenReturn(TEST_CTT_ACCESS_TOKEN);

        GitHubUserInfo userInfo = createGitHubUserInfo();
        oauthLoginService.process(OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo, null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getTermsVersion()).isEqualTo(CURRENT_TERMS_VERSION);
    }

    @Test
    @DisplayName("should set termsAcceptedAt to current timestamp when registering new OAuth user")
    void shouldSetTermsAcceptedAt_whenRegisteringNewOAuthUser() {
        when(oauthAccountRepository.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());
        when(jwtTokenProvider.generateAccessToken(any(User.class)))
                .thenReturn(TEST_CTT_ACCESS_TOKEN);

        GitHubUserInfo userInfo = createGitHubUserInfo();
        oauthLoginService.process(OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo, null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getTermsAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName(
            "should return termsExpired=false when new OAuth user registered with current terms")
    void shouldReturnTermsExpiredFalse_whenNewOAuthUserRegistered() {
        when(oauthAccountRepository.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());
        when(jwtTokenProvider.generateAccessToken(any(User.class)))
                .thenReturn(TEST_CTT_ACCESS_TOKEN);

        GitHubUserInfo userInfo = createGitHubUserInfo();
        LoginResponse response =
                oauthLoginService.process(
                        OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo, null);

        assertThat(response.termsExpired()).isFalse();
    }

    private GitHubUserInfo createGitHubUserInfo() {
        return new GitHubUserInfo(
                GITHUB_USER_ID, GITHUB_LOGIN, GITHUB_NAME, GITHUB_AVATAR_URL, TEST_EMAIL);
    }
}
