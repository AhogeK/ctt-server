package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.utils.TokenUtils;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenRefreshServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TOKEN_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String RAW_TOKEN = "test-refresh-token-abc123";
    private static final String TOKEN_HASH = TokenUtils.hashToken(RAW_TOKEN);
    private static final String NEW_ACCESS_TOKEN = "new.access.token";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_USER_AGENT = "Mozilla/5.0 Test Browser";

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuditLogService auditLogService;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityProperties.JwtProperties jwtProps;

    private TokenRefreshService tokenRefreshService;

    @BeforeEach
    void setUp() {
        when(securityProperties.jwt()).thenReturn(jwtProps);
        when(jwtProps.accessTokenTtl()).thenReturn(ACCESS_TOKEN_TTL);
        when(jwtProps.refreshTokenTtlWeb()).thenReturn(REFRESH_TOKEN_TTL);

        tokenRefreshService =
                new TokenRefreshService(
                        refreshTokenRepository,
                        userRepository,
                        jwtTokenProvider,
                        auditLogService,
                        securityProperties);
    }

    @Nested
    @DisplayName("Successful Token Refresh")
    class SuccessfulTokenRefresh {

        @Test
        @DisplayName("should rotate tokens when refresh token is valid")
        void shouldRotateTokens_whenRefreshTokenIsValid() {
            RefreshToken refreshToken = createValidRefreshToken();
            User user = createActiveUser();

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(NEW_ACCESS_TOKEN);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            LoginResponse response =
                    tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT);

            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_TTL.getSeconds());
            assertThat(refreshToken.getRevokedAt()).isNotNull();

            verify(auditLogService)
                    .log(
                            eq(USER_ID),
                            eq(AuditAction.REFRESH_TOKEN_ROTATED),
                            eq(ResourceType.REFRESH_TOKEN),
                            eq(TOKEN_ID.toString()),
                            eq(SecuritySeverity.INFO),
                            eq(
                                    AuditDetails.extension(
                                            Map.of("ip", TEST_IP, "userAgent", TEST_USER_AGENT))));
        }
    }

    @Nested
    @DisplayName("Token Validation Errors")
    class TokenValidationErrors {

        @Test
        @DisplayName("should throw AUTH_003 when token not found")
        void shouldThrowAuth003_whenTokenNotFound() {
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () -> tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_003);

            verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw AUTH_007 when refresh token expired")
        void shouldThrowAuth007_whenRefreshTokenExpired() {
            RefreshToken expiredToken = createExpiredRefreshToken();

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(
                            () -> tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_007);

            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
            verify(auditLogService, never()).logCritical(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw AUTH_009 and revoke all tokens when reuse detected")
        void shouldThrowAuth009_andRevokeAllTokens_whenReuseDetected() {
            RefreshToken revokedToken = createRevokedRefreshToken();

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(revokedToken));

            assertThatThrownBy(
                            () -> tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_009);

            verify(refreshTokenRepository).revokeAllUserTokens(eq(USER_ID), any(Instant.class));
            verify(auditLogService)
                    .logCritical(
                            eq(USER_ID),
                            eq(AuditAction.REFRESH_TOKEN_REUSE_DETECTED),
                            eq(ResourceType.REFRESH_TOKEN),
                            eq(TOKEN_ID.toString()),
                            any());
        }
    }

    @Nested
    @DisplayName("User Status Validation")
    class UserStatusValidation {

        @Test
        @DisplayName("should throw AUTH_006 when user email not verified")
        void shouldThrowAuth006_whenUserEmailNotVerified() {
            RefreshToken refreshToken = createValidRefreshToken();
            User user = createUserWithStatus(UserStatus.PENDING_VERIFICATION);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(
                            () -> tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_006);
        }

        @Test
        @DisplayName("should throw AUTH_004 when user locked")
        void shouldThrowAuth004_whenUserLocked() {
            RefreshToken refreshToken = createValidRefreshToken();
            User user = createUserWithStatus(UserStatus.LOCKED);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(
                            () -> tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_004);
        }

        @Test
        @DisplayName("should throw AUTH_005 when user suspended")
        void shouldThrowAuth005_whenUserSuspended() {
            RefreshToken refreshToken = createValidRefreshToken();
            User user = createUserWithStatus(UserStatus.SUSPENDED);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(
                            () -> tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_005);
        }

        @Test
        @DisplayName("should throw AUTH_001 when user not found")
        void shouldThrowAuth001_whenUserNotFound() {
            RefreshToken refreshToken = createValidRefreshToken();

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () -> tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_001);
        }
    }

    private User createActiveUser() {
        return createUserWithStatus(UserStatus.ACTIVE);
    }

    private User createUserWithStatus(UserStatus status) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private RefreshToken createValidRefreshToken() {
        RefreshToken token = new RefreshToken();
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", TOKEN_ID);
        token.setUserId(USER_ID);
        token.setTokenHash(TOKEN_HASH);
        token.setExpiresAt(Instant.now().plus(REFRESH_TOKEN_TTL));
        token.setIssuedFor("WEB");
        token.setDeviceId(DEVICE_ID);
        return token;
    }

    private RefreshToken createRevokedRefreshToken() {
        RefreshToken token = createValidRefreshToken();
        token.revoke();
        return token;
    }

    private RefreshToken createExpiredRefreshToken() {
        RefreshToken token = createValidRefreshToken();
        token.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
        return token;
    }
}
