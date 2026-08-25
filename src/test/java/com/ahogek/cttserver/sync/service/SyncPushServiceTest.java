package com.ahogek.cttserver.sync.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;
import com.ahogek.cttserver.sync.dto.SyncPushResponse;
import com.ahogek.cttserver.sync.dto.SyncSessionDto;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.entity.SessionChange;
import com.ahogek.cttserver.sync.enums.ChangeOp;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.sync.repository.SessionChangeRepository;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
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
class SyncPushServiceTest {

    @Mock private CodingSessionRepository codingSessionRepository;
    @Mock private SessionChangeRepository sessionChangeRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private AuditLogService auditLogService;

    private SyncPushService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new SyncPushService(
                        codingSessionRepository,
                        sessionChangeRepository,
                        deviceRepository,
                        auditLogService);
        when(deviceRepository.findByIdAndUserId(deviceId, userId))
                .thenReturn(Optional.of(new Device()));
        when(codingSessionRepository.save(any(CodingSession.class)))
                .thenAnswer(
                        inv -> {
                            CodingSession s = inv.getArgument(0);
                            if (s.getId() == null) {
                                ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
                            }
                            return s;
                        });
        when(sessionChangeRepository.findMaxChangeIdForUser(userId)).thenReturn(7L);
    }

    private SyncSessionDto dto(
            UUID sessionUuid, int clientVersion, Instant clientModifiedAt, boolean deleted) {
        return new SyncSessionDto(
                sessionUuid,
                "ctt-server",
                "Java",
                Instant.parse("2026-08-25T09:00:00Z"),
                Instant.parse("2026-08-25T10:00:00Z"),
                clientModifiedAt,
                clientVersion,
                deleted);
    }

    private CodingSession existingSession(
            UUID id, int clientVersion, Instant clientModifiedAt, long serverVersion) {
        CodingSession session = new CodingSession();
        ReflectionTestUtils.setField(session, "id", id);
        session.setUserId(userId);
        session.setSessionUuid(UUID.randomUUID());
        session.setProjectName("ctt-server");
        session.setLanguage("Java");
        session.setStartTime(Instant.parse("2026-08-25T09:00:00Z"));
        session.setEndTime(Instant.parse("2026-08-25T10:00:00Z"));
        session.setClientModifiedAt(clientModifiedAt);
        session.setClientVersion(clientVersion);
        session.setServerVersion(serverVersion);
        return session;
    }

    @Nested
    @DisplayName("push")
    class Push {

        @Test
        @DisplayName("should create a new session with server version 1 and log an upsert")
        void shouldCreateNewSession_whenSessionDoesNotExist() {
            UUID sessionUuid = UUID.randomUUID();
            when(codingSessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid))
                    .thenReturn(Optional.empty());

            SyncPushResponse response =
                    service.push(
                            userId,
                            deviceId,
                            List.of(
                                    dto(
                                            sessionUuid,
                                            1,
                                            Instant.parse("2026-08-25T10:00:00Z"),
                                            false)));

            assertThat(response.nextCursor()).isEqualTo(7);

            ArgumentCaptor<CodingSession> sessionCaptor =
                    ArgumentCaptor.forClass(CodingSession.class);
            verify(codingSessionRepository).save(sessionCaptor.capture());
            CodingSession saved = sessionCaptor.getValue();
            assertThat(saved.getSessionUuid()).isEqualTo(sessionUuid);
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getServerVersion()).isEqualTo(1);
            assertThat(saved.getUpdatedByDeviceId()).isEqualTo(deviceId);
            assertThat(saved.isDeleted()).isFalse();

            ArgumentCaptor<SessionChange> changeCaptor =
                    ArgumentCaptor.forClass(SessionChange.class);
            verify(sessionChangeRepository).save(changeCaptor.capture());
            SessionChange change = changeCaptor.getValue();
            assertThat(change.getOp()).isEqualTo(ChangeOp.UPSERT);
            assertThat(change.getServerVersion()).isEqualTo(1);
            assertThat(change.getSessionId()).isEqualTo(saved.getId());
            assertThat(change.getUserId()).isEqualTo(userId);
            assertThat(change.getDeviceId()).isEqualTo(deviceId);

            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.SYNC_PUSH,
                            ResourceType.CODING_SESSION,
                            deviceId.toString());
        }

        @Test
        @DisplayName("should apply incoming state and bump version when incoming wins LWW")
        void shouldApplyIncoming_whenIncomingWinsLww() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession existing =
                    existingSession(UUID.randomUUID(), 1, Instant.parse("2026-08-25T09:30:00Z"), 5);
            when(codingSessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid))
                    .thenReturn(Optional.of(existing));

            service.push(
                    userId,
                    deviceId,
                    List.of(dto(sessionUuid, 2, Instant.parse("2026-08-25T10:00:00Z"), false)));

            assertThat(existing.getClientVersion()).isEqualTo(2);
            assertThat(existing.getClientModifiedAt())
                    .isEqualTo(Instant.parse("2026-08-25T10:00:00Z"));
            assertThat(existing.getServerVersion()).isEqualTo(6);
            assertThat(existing.isDeleted()).isFalse();

            ArgumentCaptor<SessionChange> changeCaptor =
                    ArgumentCaptor.forClass(SessionChange.class);
            verify(sessionChangeRepository).save(changeCaptor.capture());
            assertThat(changeCaptor.getValue().getOp()).isEqualTo(ChangeOp.UPSERT);
            assertThat(changeCaptor.getValue().getServerVersion()).isEqualTo(6);
        }

        @Test
        @DisplayName("should keep existing state without change log when existing wins LWW")
        void shouldKeepExisting_whenExistingWinsLww() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession existing =
                    existingSession(UUID.randomUUID(), 5, Instant.parse("2026-08-25T10:00:00Z"), 5);
            when(codingSessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid))
                    .thenReturn(Optional.of(existing));

            service.push(
                    userId,
                    deviceId,
                    List.of(dto(sessionUuid, 3, Instant.parse("2026-08-25T09:00:00Z"), false)));

            assertThat(existing.getServerVersion()).isEqualTo(5);
            assertThat(existing.getClientVersion()).isEqualTo(5);
            verify(codingSessionRepository, never()).save(existing);
            verify(sessionChangeRepository, never()).save(any(SessionChange.class));
        }

        @Test
        @DisplayName("should soft-delete and log a delete when incoming delete wins LWW")
        void shouldSoftDelete_whenIncomingDeleteWinsLww() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession existing =
                    existingSession(UUID.randomUUID(), 1, Instant.parse("2026-08-25T09:30:00Z"), 5);
            when(codingSessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid))
                    .thenReturn(Optional.of(existing));

            service.push(
                    userId,
                    deviceId,
                    List.of(dto(sessionUuid, 2, Instant.parse("2026-08-25T10:00:00Z"), true)));

            assertThat(existing.isDeleted()).isTrue();
            assertThat(existing.getDeletedAt()).isNotNull();
            assertThat(existing.getServerVersion()).isEqualTo(6);

            ArgumentCaptor<SessionChange> changeCaptor =
                    ArgumentCaptor.forClass(SessionChange.class);
            verify(sessionChangeRepository).save(changeCaptor.capture());
            assertThat(changeCaptor.getValue().getOp()).isEqualTo(ChangeOp.DELETE);
            assertThat(changeCaptor.getValue().getServerVersion()).isEqualTo(6);
        }

        @Test
        @DisplayName("should keep existing soft-deleted session when incoming is live")
        void shouldKeepExisting_whenServerAlreadySoftDeletedAndIncomingLive() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession softDeleted =
                    existingSession(UUID.randomUUID(), 1, Instant.parse("2026-08-25T09:30:00Z"), 5);
            softDeleted.setDeleted(true);
            softDeleted.setDeletedAt(Instant.parse("2026-08-25T09:45:00Z"));
            when(codingSessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid))
                    .thenReturn(Optional.of(softDeleted));

            service.push(
                    userId,
                    deviceId,
                    List.of(dto(sessionUuid, 2, Instant.parse("2026-08-25T10:00:00Z"), false)));

            assertThat(softDeleted.isDeleted()).isTrue();
            assertThat(softDeleted.getServerVersion()).isEqualTo(5);
            verify(codingSessionRepository, never()).save(any(CodingSession.class));
            verify(sessionChangeRepository, never()).save(any(SessionChange.class));
        }

        @Test
        @DisplayName("should skip creation when client deletes a session the server never had")
        void shouldSkip_whenClientDeletesSessionServerNeverHad() {
            UUID sessionUuid = UUID.randomUUID();
            when(codingSessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid))
                    .thenReturn(Optional.empty());

            SyncPushResponse response =
                    service.push(
                            userId,
                            deviceId,
                            List.of(
                                    dto(
                                            sessionUuid,
                                            1,
                                            Instant.parse("2026-08-25T10:00:00Z"),
                                            true)));

            assertThat(response.nextCursor()).isEqualTo(7);
            verify(codingSessionRepository, never()).save(any(CodingSession.class));
            verify(sessionChangeRepository, never()).save(any(SessionChange.class));
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.SYNC_PUSH,
                            ResourceType.CODING_SESSION,
                            deviceId.toString());
        }

        @Test
        @DisplayName("should throw NotFoundException when device is not owned by user")
        void shouldThrowNotFoundException_whenDeviceNotOwned() {
            when(deviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.push(userId, deviceId, List.of()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_002);

            verify(codingSessionRepository, never()).findByUserIdAndSessionUuid(any(), any());
            verify(auditLogService)
                    .logFailure(
                            eq(userId),
                            eq(AuditAction.SYNC_PUSH),
                            eq(ResourceType.CODING_SESSION),
                            eq(deviceId.toString()),
                            eq(ErrorCode.COMMON_002.name()));
        }

        @Test
        @DisplayName("should propagate failure so the whole batch rolls back atomically")
        void shouldPropagateFailure_whenAnySessionFails() {
            UUID sessionUuid1 = UUID.randomUUID();
            UUID sessionUuid2 = UUID.randomUUID();
            when(codingSessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid1))
                    .thenReturn(Optional.empty());
            when(codingSessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid2))
                    .thenReturn(Optional.empty());
            when(codingSessionRepository.save(any(CodingSession.class)))
                    .thenAnswer(
                            inv -> {
                                CodingSession s = inv.getArgument(0);
                                if (s.getSessionUuid().equals(sessionUuid2)) {
                                    throw new IllegalStateException("persistence failure");
                                }
                                if (s.getId() == null) {
                                    ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
                                }
                                return s;
                            });

            assertThatThrownBy(
                            () ->
                                    service.push(
                                            userId,
                                            deviceId,
                                            List.of(
                                                    dto(
                                                            sessionUuid1,
                                                            1,
                                                            Instant.parse("2026-08-25T10:00:00Z"),
                                                            false),
                                                    dto(
                                                            sessionUuid2,
                                                            1,
                                                            Instant.parse("2026-08-25T10:00:00Z"),
                                                            false))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("persistence failure");

            verify(auditLogService)
                    .logFailure(
                            eq(userId),
                            eq(AuditAction.SYNC_PUSH),
                            eq(ResourceType.CODING_SESSION),
                            eq(deviceId.toString()),
                            eq(IllegalStateException.class.getSimpleName()));
        }
    }
}
