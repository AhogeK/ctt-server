package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.BatchRevokeSessionsResponse;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.device.repository.DeviceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for batch session revocation: lock ordering, de-duplication, partial success and the
 * audit trail. The endpoint-level behaviour lives in {@code LoginSessionEndpointsIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginSessionService batch revocation")
class LoginSessionServiceBatchTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private AuditLogService auditLogService;

    private LoginSessionService service;

    @BeforeEach
    void setUp() {
        service =
                new LoginSessionService(refreshTokenRepository, deviceRepository, auditLogService);
    }

    private static RefreshToken liveToken(UUID sessionId, String kind) {
        RefreshToken token = new RefreshToken();
        token.setUserId(USER_ID);
        token.setSessionId(sessionId);
        token.setIssuedFor(kind);
        return token;
    }

    private void givenLive(UUID sessionId, String kind) {
        when(refreshTokenRepository
                        .findFirstByUserIdAndSessionIdAndRevokedAtIsNullOrderByCreatedAtDesc(
                                USER_ID, sessionId))
                .thenReturn(Optional.of(liveToken(sessionId, kind)));
    }

    @Test
    @DisplayName("should lock sessions in ascending id order regardless of request order")
    void shouldLockSessionsInAscendingOrder_whenBatchSpansSeveralIds() {
        UUID low = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID middle = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID high = UUID.fromString("00000000-0000-4000-8000-000000000003");
        givenLive(low, RefreshToken.AUDIENCE_WEB);
        givenLive(middle, RefreshToken.AUDIENCE_PLUGIN);
        givenLive(high, RefreshToken.AUDIENCE_WEB);

        BatchRevokeSessionsResponse response =
                service.revokeSessions(USER_ID, List.of(high, low, middle));

        assertThat(response.revoked()).isEqualTo(3);
        assertThat(response.skipped()).isEmpty();
        InOrder order = inOrder(refreshTokenRepository);
        order.verify(refreshTokenRepository).lockSession(low);
        order.verify(refreshTokenRepository).lockSession(middle);
        order.verify(refreshTokenRepository).lockSession(high);
    }

    @Test
    @DisplayName("should skip ids with no live token and still revoke the rest")
    void shouldSkipIdsWithoutLiveToken_whenBatchIsMixed() {
        UUID live = UUID.fromString("00000000-0000-4000-8000-000000000010");
        UUID gone = UUID.fromString("00000000-0000-4000-8000-000000000011");
        givenLive(live, RefreshToken.AUDIENCE_WEB);
        when(refreshTokenRepository
                        .findFirstByUserIdAndSessionIdAndRevokedAtIsNullOrderByCreatedAtDesc(
                                USER_ID, gone))
                .thenReturn(Optional.empty());

        BatchRevokeSessionsResponse response = service.revokeSessions(USER_ID, List.of(live, gone));

        assertThat(response.revoked()).isEqualTo(1);
        assertThat(response.skipped()).containsExactly(gone);
        verify(refreshTokenRepository).revokeSession(eq(USER_ID), eq(live), any());
        verify(refreshTokenRepository, never()).revokeSession(eq(USER_ID), eq(gone), any());
    }

    @Test
    @DisplayName("should lock and revoke a repeated id once")
    void shouldLockOnce_whenIdsRepeat() {
        UUID sessionId = UUID.fromString("00000000-0000-4000-8000-000000000020");
        givenLive(sessionId, RefreshToken.AUDIENCE_WEB);

        BatchRevokeSessionsResponse response =
                service.revokeSessions(USER_ID, List.of(sessionId, sessionId, sessionId));

        assertThat(response.revoked()).isEqualTo(1);
        assertThat(response.skipped()).isEmpty();
        verify(refreshTokenRepository).lockSession(sessionId);
        verify(refreshTokenRepository).revokeSession(eq(USER_ID), eq(sessionId), any());
    }

    @Test
    @DisplayName("should reject more than 100 distinct ids before touching the database")
    void shouldRejectOversizedBatch_whenDistinctIdsExceedTheLimit() {
        List<UUID> ids = IntStream.range(0, 101).mapToObj(_ -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> service.revokeSessions(USER_ID, ids))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("100");

        verify(refreshTokenRepository, never()).lockSession(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should write one audit event per revoked session")
    void shouldWriteOneAuditEventPerRevokedSession_whenBatchSucceeds() {
        UUID web = UUID.fromString("00000000-0000-4000-8000-000000000030");
        UUID plugin = UUID.fromString("00000000-0000-4000-8000-000000000031");
        givenLive(web, RefreshToken.AUDIENCE_WEB);
        givenLive(plugin, RefreshToken.AUDIENCE_PLUGIN);

        service.revokeSessions(USER_ID, List.of(web, plugin));

        verify(auditLogService)
                .log(
                        eq(USER_ID),
                        eq(AuditAction.SESSION_REVOKED),
                        eq(ResourceType.REFRESH_TOKEN),
                        eq(web.toString()),
                        eq(SecuritySeverity.INFO),
                        any());
        verify(auditLogService)
                .log(
                        eq(USER_ID),
                        eq(AuditAction.SESSION_REVOKED),
                        eq(ResourceType.REFRESH_TOKEN),
                        eq(plugin.toString()),
                        eq(SecuritySeverity.INFO),
                        any());
    }
}
