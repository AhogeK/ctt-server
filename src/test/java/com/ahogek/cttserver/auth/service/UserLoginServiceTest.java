package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;
import com.ahogek.cttserver.user.validator.UserValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserLoginServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_PASSWORD_HASH = "hashed_password";
    private static final String TEST_ACCESS_TOKEN = "test.access.token";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    @Mock private UserRepository userRepository;
    @Mock private UserValidator userValidator;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityProperties.PasswordProperties passwordProps;
    @Mock private SecurityProperties.JwtProperties jwtProps;

    private UserLoginService loginService;

    @BeforeEach
    void setUp() {
        when(securityProperties.password()).thenReturn(passwordProps);
        when(securityProperties.jwt()).thenReturn(jwtProps);
        when(passwordProps.maxFailedAttempts()).thenReturn(MAX_FAILED_ATTEMPTS);
        when(passwordProps.lockDuration()).thenReturn(LOCK_DURATION);
        when(jwtProps.accessTokenTtl()).thenReturn(ACCESS_TOKEN_TTL);
        when(jwtProps.refreshTokenTtlWeb()).thenReturn(REFRESH_TOKEN_TTL);

        loginService =
                new UserLoginService(
                        userRepository,
                        userValidator,
                        passwordEncoder,
                        jwtTokenProvider,
                        refreshTokenRepository,
                        auditLogService,
                        securityProperties);
    }

    @Nested
    @DisplayName("Successful Login")
    class SuccessfulLogin {

        @Test
        @DisplayName("should return tokens when credentials are valid")
        void shouldReturnTokens_whenCredentialsValid() {
            User user = createActiveUser();
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(TEST_ACCESS_TOKEN);

            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);
            LoginResponse response = loginService.login(request);

            assertThat(response.userId()).isEqualTo(user.getId());
            assertThat(response.accessToken()).isEqualTo(TEST_ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_TTL.getSeconds());
        }

        @Test
        @DisplayName("should record successful login and clear failed attempts")
        void shouldRecordSuccessfulLogin_andClearFailedAttempts() {
            User user = createActiveUser();
            user.setFailedLoginAttempts(3);
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(TEST_ACCESS_TOKEN);

            loginService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null));

            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("should log successful login audit")
        void shouldLogSuccessfulLoginAudit() {
            User user = createActiveUser();
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(TEST_ACCESS_TOKEN);

            loginService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null));

            verify(auditLogService)
                    .logSuccess(
                            eq(user.getId()),
                            eq(AuditAction.LOGIN_SUCCESS),
                            eq(ResourceType.USER),
                            any());
        }
    }

    @Nested
    @DisplayName("Failed Login - Invalid Credentials")
    class FailedLoginInvalidCredentials {

        @Test
        @DisplayName("should throw AUTH_001 when user not found")
        void shouldThrowAuth001_whenUserNotFound() {
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());

            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            assertThatThrownBy(() -> loginService.login(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_001);
        }

        @Test
        @DisplayName("should throw AUTH_001 when password is wrong")
        void shouldThrowAuth001_whenPasswordWrong() {
            User user = createActiveUser();
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            assertThatThrownBy(() -> loginService.login(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_001);
        }

        @Test
        @DisplayName("should increment failed attempts on wrong password")
        void shouldIncrementFailedAttempts_onWrongPassword() {
            User user = createActiveUser();
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

            try {
                loginService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null));
            } catch (UnauthorizedException _) {
                // expected
            }

            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("should log failed login audit on wrong password")
        void shouldLogFailedLoginAudit_onWrongPassword() {
            User user = createActiveUser();
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(TEST_PASSWORD, TEST_PASSWORD_HASH)).thenReturn(false);

            try {
                loginService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null));
            } catch (UnauthorizedException _) {
                // expected
            }

            verify(auditLogService)
                    .logFailure(
                            eq(user.getId()),
                            eq(AuditAction.LOGIN_FAILED),
                            eq(ResourceType.USER),
                            any(),
                            any());
        }
    }

    @Nested
    @DisplayName("User Status Validation")
    class UserStatusValidation {

        @Test
        @DisplayName("should throw AUTH_006 when user is PENDING_VERIFICATION")
        void shouldThrowAuth006_whenPendingVerification() {
            User user = createUserWithStatus(UserStatus.PENDING_VERIFICATION);
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));

            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            assertThatThrownBy(() -> loginService.login(request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_006);
        }

        @Test
        @DisplayName("should throw AUTH_004 when user is LOCKED")
        void shouldThrowAuth004_whenLocked() {
            User user = createUserWithStatus(UserStatus.LOCKED);
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));

            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            assertThatThrownBy(() -> loginService.login(request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_004);
        }

        @Test
        @DisplayName("should throw AUTH_005 when user is SUSPENDED")
        void shouldThrowAuth005_whenSuspended() {
            User user = createUserWithStatus(UserStatus.SUSPENDED);
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));

            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            assertThatThrownBy(() -> loginService.login(request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_005);
        }

        @Test
        @DisplayName("should throw AUTH_005 when user is DELETED")
        void shouldThrowAuth005_whenDeleted() {
            User user = createUserWithStatus(UserStatus.DELETED);
            when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(user));

            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD, null);

            assertThatThrownBy(() -> loginService.login(request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_005);
        }
    }

    private User createActiveUser() {
        return createUserWithStatus(UserStatus.ACTIVE);
    }

    private User createUserWithStatus(UserStatus status) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail(TEST_EMAIL);
        user.setDisplayName("Test User");
        user.setPasswordHash(TEST_PASSWORD_HASH);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
