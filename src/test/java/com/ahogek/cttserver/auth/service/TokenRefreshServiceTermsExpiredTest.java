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
import com.ahogek.cttserver.common.config.properties.TermsProperties;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenRefreshServiceTermsExpiredTest {

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
    private static final String CURRENT_TERMS_VERSION = "1.0.0";

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuditLogService auditLogService;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityProperties.JwtProperties jwtProps;
    @Mock private TermsProperties termsProperties;

    private TokenRefreshService tokenRefreshService;

    @BeforeEach
    void setUp() {
        when(securityProperties.jwt()).thenReturn(jwtProps);
        when(jwtProps.accessTokenTtl()).thenReturn(ACCESS_TOKEN_TTL);
        when(jwtProps.refreshTokenTtlWeb()).thenReturn(REFRESH_TOKEN_TTL);
        when(termsProperties.currentVersion()).thenReturn(CURRENT_TERMS_VERSION);

        tokenRefreshService =
                new TokenRefreshService(
                        refreshTokenRepository,
                        userRepository,
                        jwtTokenProvider,
                        auditLogService,
                        securityProperties,
                        termsProperties);
    }

    @Nested
    @DisplayName("Terms Expired Status")
    class TermsExpiredStatus {

        @Test
        @DisplayName("should return termsExpired=false when user has current terms version")
        void shouldReturnTermsExpiredFalse_whenUserHasCurrentVersion() {
            RefreshToken refreshToken = createValidRefreshToken();
            User user = createActiveUserWithTermsVersion(CURRENT_TERMS_VERSION);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(NEW_ACCESS_TOKEN);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            LoginResponse response =
                    tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT);

            assertThat(response.termsExpired()).isFalse();
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
            assertThat(response.tokenType()).isEqualTo("Bearer");

            verify(auditLogService)
                    .log(
                            USER_ID,
                            AuditAction.REFRESH_TOKEN_ROTATED,
                            ResourceType.REFRESH_TOKEN,
                            TOKEN_ID.toString(),
                            SecuritySeverity.INFO,
                            AuditDetails.extension(
                                    Map.of("ip", TEST_IP, "userAgent", TEST_USER_AGENT)));
        }

        @Test
        @DisplayName("should return termsExpired=true when user has null terms version")
        void shouldReturnTermsExpiredTrue_whenUserHasNullTermsVersion() {
            RefreshToken refreshToken = createValidRefreshToken();
            User user = createActiveUserWithTermsVersion(null);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(NEW_ACCESS_TOKEN);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            LoginResponse response =
                    tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT);

            assertThat(response.termsExpired()).isTrue();
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
        }

        @Test
        @DisplayName("should return termsExpired=true when user has outdated terms version")
        void shouldReturnTermsExpiredTrue_whenUserHasOutdatedVersion() {
            RefreshToken refreshToken = createValidRefreshToken();
            User user = createActiveUserWithTermsVersion("0.9.0");

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(user)).thenReturn(NEW_ACCESS_TOKEN);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            LoginResponse response =
                    tokenRefreshService.refresh(RAW_TOKEN, TEST_IP, TEST_USER_AGENT);

            assertThat(response.termsExpired()).isTrue();
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
        }
    }

    private User createActiveUserWithTermsVersion(String termsVersion) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        org.springframework.test.util.ReflectionTestUtils.setField(
                user, "status", UserStatus.ACTIVE);
        user.setTermsVersion(termsVersion);
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
}
