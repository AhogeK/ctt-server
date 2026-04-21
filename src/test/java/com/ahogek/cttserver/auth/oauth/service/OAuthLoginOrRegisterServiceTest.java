package com.ahogek.cttserver.auth.oauth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.oauth.entity.UserOAuthAccount;
import com.ahogek.cttserver.auth.oauth.enums.OAuthProvider;
import com.ahogek.cttserver.auth.oauth.model.GitHubUserInfo;
import com.ahogek.cttserver.auth.oauth.repository.UserOAuthAccountRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.auth.service.JwtTokenProvider;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthLoginOrRegisterServiceTest {

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

    @Mock private UserOAuthAccountRepository oauthAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityProperties.JwtProperties jwtProps;

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
                        securityProperties);

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

    @Nested
    @DisplayName("Existing OAuth Binding")
    class ExistingOAuthBinding {

        @Test
        @DisplayName("should return tokens when OAuth binding exists and user is ACTIVE")
        void shouldReturnTokens_whenBindingExistsAndUserActive() {
            User user = createActiveUser();
            UserOAuthAccount account = createOAuthAccount(user);
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.of(account));
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(TEST_CTT_ACCESS_TOKEN);

            GitHubUserInfo userInfo = createGitHubUserInfo();
            LoginResponse response =
                    oauthLoginService.process(
                            OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo);

            assertThat(response.userId()).isEqualTo(user.getId());
            assertThat(response.accessToken()).isEqualTo(TEST_CTT_ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_TTL.getSeconds());
        }

        @Test
        @DisplayName("should update OAuth account tokens on login")
        void shouldUpdateOAuthAccountTokens_onLogin() {
            User user = createActiveUser();
            UserOAuthAccount account = createOAuthAccount(user);
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.of(account));
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(TEST_CTT_ACCESS_TOKEN);

            GitHubUserInfo userInfo = createGitHubUserInfo();
            oauthLoginService.process(OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo);

            verify(oauthAccountRepository).save(any(UserOAuthAccount.class));
        }

        @Test
        @DisplayName("should log OAUTH_LOGIN_SUCCESS audit on successful login")
        void shouldLogOauthLoginSuccess_onSuccessfulLogin() {
            User user = createActiveUser();
            UserOAuthAccount account = createOAuthAccount(user);
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.of(account));
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(TEST_CTT_ACCESS_TOKEN);

            GitHubUserInfo userInfo = createGitHubUserInfo();
            oauthLoginService.process(OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo);

            verify(auditLogService)
                    .logSuccess(
                            user.getId(),
                            AuditAction.OAUTH_LOGIN_SUCCESS,
                            ResourceType.USER,
                            user.getId().toString());
        }
    }

    @Nested
    @DisplayName("New OAuth Binding - Email Merge")
    class NewOAuthBindingEmailMerge {

        @Test
        @DisplayName("should link OAuth account when email already exists")
        void shouldLinkOAuthAccount_whenEmailAlreadyExists() {
            User existingUser = createActiveUser();
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL))
                    .thenReturn(Optional.of(existingUser));
            when(jwtTokenProvider.generateAccessToken(existingUser))
                    .thenReturn(TEST_CTT_ACCESS_TOKEN);

            GitHubUserInfo userInfo = createGitHubUserInfo();
            LoginResponse response =
                    oauthLoginService.process(
                            OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo);

            assertThat(response.userId()).isEqualTo(existingUser.getId());
            assertThat(response.accessToken()).isEqualTo(TEST_CTT_ACCESS_TOKEN);
        }

        @Test
        @DisplayName("should log OAUTH_ACCOUNT_LINKED when merging with existing user")
        void shouldLogOauthAccountLinked_whenMergingWithExistingUser() {
            User existingUser = createActiveUser();
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL))
                    .thenReturn(Optional.of(existingUser));
            when(jwtTokenProvider.generateAccessToken(existingUser))
                    .thenReturn(TEST_CTT_ACCESS_TOKEN);

            GitHubUserInfo userInfo = createGitHubUserInfo();
            oauthLoginService.process(OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo);

            verify(auditLogService)
                    .logSuccess(
                            existingUser.getId(),
                            AuditAction.OAUTH_ACCOUNT_LINKED,
                            ResourceType.USER,
                            existingUser.getId().toString());
        }
    }

    @Nested
    @DisplayName("New OAuth Binding - New User Registration")
    class NewOAuthBindingNewUser {

        @Test
        @DisplayName("should log OAUTH_LOGIN_SUCCESS on new user registration")
        void shouldLogOauthLoginSuccess_onNewUserRegistration() {
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());
            when(jwtTokenProvider.generateAccessToken(any(User.class)))
                    .thenReturn(TEST_CTT_ACCESS_TOKEN);

            GitHubUserInfo userInfo = createGitHubUserInfo();
            oauthLoginService.process(OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo);

            verify(auditLogService)
                    .logSuccess(
                            any(UUID.class),
                            eq(AuditAction.OAUTH_LOGIN_SUCCESS),
                            eq(ResourceType.USER),
                            any());
        }

        @Test
        @DisplayName("should create user with ACTIVE status and emailVerified=true")
        void shouldCreateUserWithActiveStatus_andEmailVerified() {
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());
            when(jwtTokenProvider.generateAccessToken(any(User.class)))
                    .thenReturn(TEST_CTT_ACCESS_TOKEN);

            GitHubUserInfo userInfo = createGitHubUserInfo();
            oauthLoginService.process(OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo);

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should use login as displayName when name is null")
        void shouldUseLoginAsDisplayName_whenNameIsNull() {
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());
            when(jwtTokenProvider.generateAccessToken(any(User.class)))
                    .thenReturn(TEST_CTT_ACCESS_TOKEN);

            GitHubUserInfo userInfo =
                    new GitHubUserInfo(
                            GITHUB_USER_ID, GITHUB_LOGIN, null, GITHUB_AVATAR_URL, TEST_EMAIL);
            oauthLoginService.process(OAuthProvider.GITHUB, TEST_GITHUB_ACCESS_TOKEN, userInfo);

            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("User Status Validation")
    class UserStatusValidation {

        @Test
        @DisplayName("should throw AUTH_004 when user is LOCKED")
        void shouldThrowAuth004_whenUserLocked() {
            User lockedUser = createUserWithStatus(UserStatus.LOCKED);
            UserOAuthAccount account = createOAuthAccount(lockedUser);
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.of(account));

            GitHubUserInfo userInfo = createGitHubUserInfo();

            assertThatThrownBy(
                            () ->
                                    oauthLoginService.process(
                                            OAuthProvider.GITHUB,
                                            TEST_GITHUB_ACCESS_TOKEN,
                                            userInfo))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_004);
        }

        @Test
        @DisplayName("should throw AUTH_005 when user is SUSPENDED")
        void shouldThrowAuth005_whenUserSuspended() {
            User suspendedUser = createUserWithStatus(UserStatus.SUSPENDED);
            UserOAuthAccount account = createOAuthAccount(suspendedUser);
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.of(account));

            GitHubUserInfo userInfo = createGitHubUserInfo();

            assertThatThrownBy(
                            () ->
                                    oauthLoginService.process(
                                            OAuthProvider.GITHUB,
                                            TEST_GITHUB_ACCESS_TOKEN,
                                            userInfo))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_005);
        }

        @Test
        @DisplayName("should throw AUTH_005 when user is DELETED")
        void shouldThrowAuth005_whenUserDeleted() {
            User deletedUser = createUserWithStatus(UserStatus.DELETED);
            UserOAuthAccount account = createOAuthAccount(deletedUser);
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.of(account));

            GitHubUserInfo userInfo = createGitHubUserInfo();

            assertThatThrownBy(
                            () ->
                                    oauthLoginService.process(
                                            OAuthProvider.GITHUB,
                                            TEST_GITHUB_ACCESS_TOKEN,
                                            userInfo))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_005);
        }

        @Test
        @DisplayName("should throw AUTH_006 when user is PENDING_VERIFICATION")
        void shouldThrowAuth006_whenUserPendingVerification() {
            User pendingUser = createUserWithStatus(UserStatus.PENDING_VERIFICATION);
            UserOAuthAccount account = createOAuthAccount(pendingUser);
            when(oauthAccountRepository.findByProviderAndProviderUserId(
                            OAuthProvider.GITHUB, String.valueOf(GITHUB_USER_ID)))
                    .thenReturn(Optional.of(account));

            GitHubUserInfo userInfo = createGitHubUserInfo();

            assertThatThrownBy(
                            () ->
                                    oauthLoginService.process(
                                            OAuthProvider.GITHUB,
                                            TEST_GITHUB_ACCESS_TOKEN,
                                            userInfo))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_006);
        }
    }

    private User createActiveUser() {
        return createUserWithStatus(UserStatus.ACTIVE);
    }

    private User createUserWithStatus(UserStatus status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail(TEST_EMAIL);
        user.setDisplayName("OAuth User");
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private UserOAuthAccount createOAuthAccount(User user) {
        UserOAuthAccount account = new UserOAuthAccount();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        account.setUser(user);
        account.setProvider(OAuthProvider.GITHUB);
        account.setProviderUserId(String.valueOf(GITHUB_USER_ID));
        account.setProviderLogin(GITHUB_LOGIN);
        account.setProviderEmail(TEST_EMAIL);
        return account;
    }

    private GitHubUserInfo createGitHubUserInfo() {
        return new GitHubUserInfo(
                GITHUB_USER_ID, GITHUB_LOGIN, GITHUB_NAME, GITHUB_AVATAR_URL, TEST_EMAIL);
    }
}
