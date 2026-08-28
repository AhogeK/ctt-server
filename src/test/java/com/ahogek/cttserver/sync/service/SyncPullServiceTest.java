package com.ahogek.cttserver.sync.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;
import com.ahogek.cttserver.sync.dto.SyncChangeDto;
import com.ahogek.cttserver.sync.dto.SyncPullResponse;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.entity.SessionChange;
import com.ahogek.cttserver.sync.entity.SyncCursor;
import com.ahogek.cttserver.sync.enums.ChangeOp;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.sync.repository.SessionChangeRepository;
import com.ahogek.cttserver.sync.repository.SyncCursorRepository;

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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SyncPullServiceTest {

    @Mock private SessionChangeRepository sessionChangeRepository;
    @Mock private CodingSessionRepository codingSessionRepository;
    @Mock private SyncCursorRepository syncCursorRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private AuditLogService auditLogService;

    private SyncPullService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new SyncPullService(
                        sessionChangeRepository,
                        codingSessionRepository,
                        syncCursorRepository,
                        deviceRepository,
                        auditLogService);
        when(deviceRepository.findByIdAndUserId(deviceId, userId))
                .thenReturn(Optional.of(new Device()));
    }

    private SyncCursor cursor(long watermark) {
        SyncCursor cursor = new SyncCursor();
        cursor.setUserId(userId);
        cursor.setDeviceId(deviceId);
        cursor.setLastPulledChangeId(watermark);
        return cursor;
    }

    private SessionChange change(long changeId, UUID sessionId, ChangeOp op, long serverVersion) {
        SessionChange change = new SessionChange();
        ReflectionTestUtils.setField(change, "changeId", changeId);
        change.setUserId(userId);
        change.setSessionId(sessionId);
        change.setOp(op);
        change.setServerVersion(serverVersion);
        return change;
    }

    private UUID sessionUuidFor(UUID sessionId) {
        return UUID.nameUUIDFromBytes(sessionId.toString().getBytes());
    }

    private CodingSession session(UUID id, boolean deleted) {
        CodingSession session = new CodingSession();
        ReflectionTestUtils.setField(session, "id", id);
        session.setUserId(userId);
        session.setSessionUuid(sessionUuidFor(id));
        session.setProjectName("ctt-server");
        session.setLanguage("Java");
        session.setStartTime(Instant.parse("2026-08-25T09:00:00Z"));
        session.setEndTime(Instant.parse("2026-08-25T10:00:00Z"));
        session.setClientModifiedAt(Instant.parse("2026-08-25T10:00:00Z"));
        session.setClientVersion(2);
        session.setServerVersion(3);
        session.setDeleted(deleted);
        return session;
    }

    @Nested
    @DisplayName("pull")
    class Pull {

        @Test
        @DisplayName("should return incremental changes and advance the cursor")
        void shouldReturnIncrementalChanges_whenChangesExist() {
            UUID sessionId1 = UUID.randomUUID();
            UUID sessionId2 = UUID.randomUUID();
            when(syncCursorRepository.findByUserIdAndDeviceId(userId, deviceId))
                    .thenReturn(Optional.of(cursor(10)));
            when(sessionChangeRepository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
                            10, userId))
                    .thenReturn(
                            List.of(
                                    change(11, sessionId1, ChangeOp.UPSERT, 3),
                                    change(12, sessionId2, ChangeOp.DELETE, 4)));
            when(codingSessionRepository.findAllByIdIn(any()))
                    .thenReturn(List.of(session(sessionId1, false), session(sessionId2, true)));

            SyncPullResponse response = service.pull(userId, deviceId, 10);

            assertThat(response.nextCursor()).isEqualTo(12);
            assertThat(response.changes()).hasSize(2);
            SyncChangeDto first = response.changes().getFirst();
            assertThat(first.changeId()).isEqualTo(11);
            assertThat(first.sessionId()).isEqualTo(sessionId1);
            assertThat(first.sessionUuid()).isEqualTo(sessionUuidFor(sessionId1));
            assertThat(first.op()).isEqualTo(ChangeOp.UPSERT);
            assertThat(first.serverVersion()).isEqualTo(3);
            assertThat(first.projectName()).isEqualTo("ctt-server");
            assertThat(first.language()).isEqualTo("Java");
            assertThat(first.deleted()).isFalse();
            SyncChangeDto second = response.changes().get(1);
            assertThat(second.changeId()).isEqualTo(12);
            assertThat(second.sessionUuid()).isEqualTo(sessionUuidFor(sessionId2));
            assertThat(second.op()).isEqualTo(ChangeOp.DELETE);
            assertThat(second.deleted()).isTrue();

            verify(syncCursorRepository).advancePullWatermark(userId, deviceId, 12);
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.SYNC_PULL,
                            ResourceType.CODING_SESSION,
                            deviceId.toString());
        }

        @Test
        @DisplayName("should return empty changes and the current cursor when nothing changed")
        void shouldReturnEmpty_whenNoNewChanges() {
            when(syncCursorRepository.findByUserIdAndDeviceId(userId, deviceId))
                    .thenReturn(Optional.of(cursor(10)));
            when(sessionChangeRepository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
                            10, userId))
                    .thenReturn(List.of());

            SyncPullResponse response = service.pull(userId, deviceId, 10);

            assertThat(response.changes()).isEmpty();
            assertThat(response.nextCursor()).isEqualTo(10);
            verify(syncCursorRepository).advancePullWatermark(userId, deviceId, 10);
        }

        @Test
        @DisplayName("should treat a fresh device as watermark zero")
        void shouldTreatFreshDeviceAsWatermarkZero() {
            UUID sessionId = UUID.randomUUID();
            when(syncCursorRepository.findByUserIdAndDeviceId(userId, deviceId))
                    .thenReturn(Optional.empty());
            when(sessionChangeRepository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
                            0, userId))
                    .thenReturn(List.of(change(5, sessionId, ChangeOp.UPSERT, 1)));
            when(codingSessionRepository.findAllByIdIn(any()))
                    .thenReturn(List.of(session(sessionId, false)));

            SyncPullResponse response = service.pull(userId, deviceId, 0);

            assertThat(response.changes()).hasSize(1);
            assertThat(response.nextCursor()).isEqualTo(5);
            verify(syncCursorRepository).advancePullWatermark(userId, deviceId, 5);
        }

        @Test
        @DisplayName("should yield null sessionUuid when session is missing")
        void shouldYieldNullSessionUuid_whenSessionMissing() {
            UUID sessionId = UUID.randomUUID();
            when(syncCursorRepository.findByUserIdAndDeviceId(userId, deviceId))
                    .thenReturn(Optional.of(cursor(10)));
            when(sessionChangeRepository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
                            10, userId))
                    .thenReturn(List.of(change(11, sessionId, ChangeOp.DELETE, 4)));
            when(codingSessionRepository.findAllByIdIn(any())).thenReturn(List.of());

            SyncPullResponse response = service.pull(userId, deviceId, 10);

            assertThat(response.changes()).hasSize(1);
            assertThat(response.changes().getFirst().sessionUuid()).isNull();
        }

        @Test
        @DisplayName("should throw NotFoundException when device is not owned by user")
        void shouldThrowNotFoundException_whenDeviceNotOwned() {
            when(deviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.pull(userId, deviceId, 0))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_002);

            verify(sessionChangeRepository, never())
                    .findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(anyLong(), any());
            verify(auditLogService)
                    .logFailure(
                            eq(userId),
                            eq(AuditAction.SYNC_PULL),
                            eq(ResourceType.CODING_SESSION),
                            eq(deviceId.toString()),
                            eq(ErrorCode.COMMON_002.name()));
        }
    }
}
