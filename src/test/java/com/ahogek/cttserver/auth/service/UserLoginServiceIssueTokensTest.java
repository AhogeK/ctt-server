package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.lockout.LoginAttemptService;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.config.properties.TermsProperties;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UserLoginService.issueTokens() method tests.
 *
 * <p>Validates token generation logic for OAuth callback and password reset flows. Service layer
 * tests are pure unit tests with Mockito, fully isolated from Spring container.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-05-05
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserLoginService.issueTokens() Tests")
class UserLoginServiceIssueTokensTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ACCESS_TOKEN = "test.access.token";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final String CURRENT_TERMS_VERSION = "1.0.0";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityProperties.JwtProperties jwtProps;
    @Mock private TermsProperties termsProperties;

    private UserLoginService userLoginService;

    @BeforeEach
    void setUp() {
        when(securityProperties.jwt()).thenReturn(jwtProps);
        when(jwtProps.accessTokenTtl()).thenReturn(ACCESS_TOKEN_TTL);
        when(jwtProps.refreshTokenTtlWeb()).thenReturn(REFRESH_TOKEN_TTL);
        when(termsProperties.currentVersion()).thenReturn(CURRENT_TERMS_VERSION);

        userLoginService =
                new UserLoginService(
                        userRepository,
                        passwordEncoder,
                        jwtTokenProvider,
                        refreshTokenRepository,
                        auditLogService,
                        loginAttemptService,
                        securityProperties,
                        termsProperties);
    }

    @Nested
    @DisplayName("Terms Version Validation")
    class TermsVersionValidation {

        @Test
        @DisplayName(
                "should issue tokens with termsExpired=false when user has current terms version")
        void shouldIssueTokensWithTermsNotExpired() {
            // Given
            User user = createActiveUser();
            user.setTermsVersion(CURRENT_TERMS_VERSION);

            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(ACCESS_TOKEN);
            when(refreshTokenRepository.save(any()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            LoginResponse response = userLoginService.issueTokens(user);

            // Then
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_TTL.getSeconds());
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.termsExpired()).isFalse();
        }

        @Test
        @DisplayName("should issue tokens with termsExpired=true when user has null terms version")
        void shouldIssueTokensWithTermsExpired_whenUserHasNullVersion() {
            // Given
            User user = createActiveUser();
            user.setTermsVersion(null);

            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(ACCESS_TOKEN);
            when(refreshTokenRepository.save(any()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            LoginResponse response = userLoginService.issueTokens(user);

            // Then
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_TTL.getSeconds());
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.termsExpired()).isTrue();
        }

        @Test
        @DisplayName("should issue tokens with termsExpired=true when user has old terms version")
        void shouldIssueTokensWithTermsExpired_whenUserHasOldVersion() {
            // Given
            User user = createActiveUser();
            user.setTermsVersion("0.9.0");

            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(ACCESS_TOKEN);
            when(refreshTokenRepository.save(any()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            LoginResponse response = userLoginService.issueTokens(user);

            // Then
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_TTL.getSeconds());
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.termsExpired()).isTrue();
        }
    }

    private User createActiveUser() {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        org.springframework.test.util.ReflectionTestUtils.setField(
                user, "status", UserStatus.ACTIVE);
        return user;
    }
}
