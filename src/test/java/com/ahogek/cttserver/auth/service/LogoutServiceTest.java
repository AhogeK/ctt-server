package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.auth.enums.TokenStatus;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.utils.TokenUtils;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogoutServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ATTACKER_USER_ID = UUID.randomUUID();
    private static final UUID TOKEN_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String RAW_TOKEN = "test-refresh-token-abc123";
    private static final String TOKEN_HASH = TokenUtils.hashToken(RAW_TOKEN);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogService auditLogService;

    private LogoutService logoutService;

    @BeforeEach
    void setUp() {
        logoutService = new LogoutService(refreshTokenRepository, auditLogService);
    }

    @Nested
    @DisplayName("Happy Path - Successful Logout")
    class HappyPath {

        @Test
        @DisplayName("should revoke valid token and log audit event")
        void shouldRevokeValidToken_andLogAudit() {
            RefreshToken refreshToken = createValidRefreshToken(USER_ID);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            logoutService.logout(USER_ID, RAW_TOKEN);

            assertThat(refreshToken.getRevokedAt()).isNotNull();
            assertThat(refreshToken.determineStatus()).isEqualTo(TokenStatus.REVOKED);

            verify(refreshTokenRepository).save(refreshToken);
            verify(auditLogService)
                    .logSuccess(
                            eq(USER_ID),
                            eq(AuditAction.LOGOUT_SUCCESS),
                            eq(ResourceType.REFRESH_TOKEN),
                            eq(TOKEN_ID.toString()));
        }
    }

    @Nested
    @DisplayName("Idempotency - Tolerance for Edge Cases")
    class Idempotency {

        @Test
        @DisplayName("should silently return when token not found")
        void shouldSilentlyReturn_whenTokenNotFound() {
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            logoutService.logout(USER_ID, RAW_TOKEN);

            verify(refreshTokenRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
            verify(auditLogService, never()).logCritical(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return immediately when token is null")
        void shouldReturnImmediately_whenTokenIsNull() {
            logoutService.logout(USER_ID, null);

            verify(refreshTokenRepository, never()).findByTokenHash(any());
            verify(refreshTokenRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return immediately when token is blank")
        void shouldReturnImmediately_whenTokenIsBlank() {
            logoutService.logout(USER_ID, "   ");

            verify(refreshTokenRepository, never()).findByTokenHash(any());
            verify(refreshTokenRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("BOLA Defense - Ownership Validation")
    class BolaDefense {

        @Test
        @DisplayName("should log security alert when user attempts to revoke another user's token")
        void shouldLogSecurityAlert_whenUserAttemptsToRevokeAnotherUsersToken() {
            RefreshToken victimToken = createValidRefreshToken(ATTACKER_USER_ID);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(victimToken));

            logoutService.logout(USER_ID, RAW_TOKEN);

            verify(auditLogService)
                    .logCritical(
                            eq(USER_ID),
                            eq(AuditAction.SECURITY_ALERT),
                            eq(ResourceType.REFRESH_TOKEN),
                            eq(TOKEN_ID.toString()),
                            eq(
                                    AuditDetails.reason(
                                            "BOLA attack: attempted to revoke another user's token")));
        }

        @Test
        @DisplayName("should not revoke token when ownership mismatch")
        void shouldNotRevokeToken_whenOwnershipMismatch() {
            RefreshToken victimToken = createValidRefreshToken(ATTACKER_USER_ID);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(victimToken));

            logoutService.logout(USER_ID, RAW_TOKEN);

            assertThat(victimToken.getRevokedAt()).isNull();
            assertThat(victimToken.determineStatus()).isEqualTo(TokenStatus.VALID);

            verify(refreshTokenRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Token Status Handling")
    class TokenStatusHandling {

        @Test
        @DisplayName("should not revoke already revoked token")
        void shouldNotRevokeAlreadyRevokedToken() {
            RefreshToken revokedToken = createRevokedRefreshToken(USER_ID);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(revokedToken));

            logoutService.logout(USER_ID, RAW_TOKEN);

            assertThat(revokedToken.determineStatus()).isEqualTo(TokenStatus.REVOKED);

            verify(refreshTokenRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should not revoke expired token")
        void shouldNotRevokeExpiredToken() {
            RefreshToken expiredToken = createExpiredRefreshToken(USER_ID);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(expiredToken));

            logoutService.logout(USER_ID, RAW_TOKEN);

            assertThat(expiredToken.determineStatus()).isEqualTo(TokenStatus.EXPIRED);
            assertThat(expiredToken.getRevokedAt()).isNull();

            verify(refreshTokenRepository, never()).save(any());
            verify(auditLogService, never()).logSuccess(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Audit Logging Verification")
    class AuditLoggingVerification {

        @Test
        @DisplayName("should log LOGOUT_SUCCESS audit event on successful logout")
        void shouldLogLOGOUT_SUCCESS_auditEvent() {
            RefreshToken refreshToken = createValidRefreshToken(USER_ID);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            logoutService.logout(USER_ID, RAW_TOKEN);

            verify(auditLogService)
                    .logSuccess(
                            eq(USER_ID),
                            eq(AuditAction.LOGOUT_SUCCESS),
                            eq(ResourceType.REFRESH_TOKEN),
                            eq(TOKEN_ID.toString()));
        }

        @Test
        @DisplayName("should log SECURITY_ALERT audit event on BOLA attempt")
        void shouldLogSECURITY_ALERT_auditEvent() {
            RefreshToken victimToken = createValidRefreshToken(ATTACKER_USER_ID);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(victimToken));

            logoutService.logout(USER_ID, RAW_TOKEN);

            verify(auditLogService)
                    .logCritical(
                            eq(USER_ID),
                            eq(AuditAction.SECURITY_ALERT),
                            eq(ResourceType.REFRESH_TOKEN),
                            eq(TOKEN_ID.toString()),
                            eq(
                                    AuditDetails.reason(
                                            "BOLA attack: attempted to revoke another user's token")));
        }
    }

    private RefreshToken createValidRefreshToken(UUID userId) {
        RefreshToken token = new RefreshToken();
        org.springframework.test.util.ReflectionTestUtils.setField(token, "id", TOKEN_ID);
        token.setUserId(userId);
        token.setTokenHash(TOKEN_HASH);
        token.setExpiresAt(Instant.now().plus(REFRESH_TOKEN_TTL));
        token.setIssuedFor("WEB");
        token.setDeviceId(DEVICE_ID);
        return token;
    }

    private RefreshToken createRevokedRefreshToken(UUID userId) {
        RefreshToken token = createValidRefreshToken(userId);
        token.revoke();
        return token;
    }

    private RefreshToken createExpiredRefreshToken(UUID userId) {
        RefreshToken token = createValidRefreshToken(userId);
        token.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
        return token;
    }
}
