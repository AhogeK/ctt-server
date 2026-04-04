package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogoutServiceLogoutAllTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogService auditLogService;

    private LogoutService logoutService;

    @BeforeEach
    void setUp() {
        logoutService = new LogoutService(refreshTokenRepository, auditLogService);
    }

    @Nested
    @DisplayName("Happy Path - Global Logout")
    class HappyPath {

        @Test
        @DisplayName("should revoke all active tokens and log audit event")
        void shouldRevokeAllActiveTokens_andLogAudit() {
            // Given
            int revokedCount = 5;

            when(refreshTokenRepository.revokeAllUserTokens(eq(USER_ID), any(Instant.class)))
                    .thenReturn(revokedCount);

            // When
            logoutService.logoutAll(USER_ID);

            // Then
            verify(refreshTokenRepository).revokeAllUserTokens(eq(USER_ID), any(Instant.class));

            ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(refreshTokenRepository).revokeAllUserTokens(eq(USER_ID), timeCaptor.capture());
            assertThat(timeCaptor.getValue()).isBeforeOrEqualTo(Instant.now());

            verify(auditLogService)
                    .logSuccess(
                            USER_ID,
                            AuditAction.LOGOUT_ALL_DEVICES,
                            ResourceType.USER,
                            USER_ID.toString());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle zero tokens revoked")
        void shouldHandleZeroTokensRevoked() {
            // Given
            when(refreshTokenRepository.revokeAllUserTokens(eq(USER_ID), any(Instant.class)))
                    .thenReturn(0);

            // When
            logoutService.logoutAll(USER_ID);

            // Then
            verify(refreshTokenRepository).revokeAllUserTokens(eq(USER_ID), any(Instant.class));
            verify(auditLogService)
                    .logSuccess(
                            USER_ID,
                            AuditAction.LOGOUT_ALL_DEVICES,
                            ResourceType.USER,
                            USER_ID.toString());
        }

        @Test
        @DisplayName("should handle large number of tokens revoked")
        void shouldHandleLargeNumberOfTokensRevoked() {
            // Given
            int revokedCount = 1000;
            when(refreshTokenRepository.revokeAllUserTokens(eq(USER_ID), any(Instant.class)))
                    .thenReturn(revokedCount);

            // When
            logoutService.logoutAll(USER_ID);

            // Then
            verify(refreshTokenRepository).revokeAllUserTokens(eq(USER_ID), any(Instant.class));
            verify(auditLogService)
                    .logSuccess(
                            USER_ID,
                            AuditAction.LOGOUT_ALL_DEVICES,
                            ResourceType.USER,
                            USER_ID.toString());
        }
    }

    @Nested
    @DisplayName("Audit Logging Verification")
    class AuditLoggingVerification {

        @Test
        @DisplayName("should log LOGOUT_ALL_DEVICES audit event with correct parameters")
        void shouldLogLOGOUT_ALL_DEVICES_auditEvent() {
            // Given
            when(refreshTokenRepository.revokeAllUserTokens(eq(USER_ID), any(Instant.class)))
                    .thenReturn(1);

            // When
            logoutService.logoutAll(USER_ID);

            // Then
            verify(auditLogService)
                    .logSuccess(
                            USER_ID,
                            AuditAction.LOGOUT_ALL_DEVICES,
                            ResourceType.USER,
                            USER_ID.toString());
        }
    }
}
