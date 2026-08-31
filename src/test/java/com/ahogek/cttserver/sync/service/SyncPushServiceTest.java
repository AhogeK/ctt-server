package com.ahogek.cttserver.sync.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;
import com.ahogek.cttserver.leaderboard.service.LeaderboardService;
import com.ahogek.cttserver.sync.dto.SyncPushResponse;
import com.ahogek.cttserver.sync.dto.SyncSessionDto;
import com.ahogek.cttserver.sync.entity.CodingSession;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SyncPushServiceTest {

    @Mock private CodingSessionRepository codingSessionRepository;
    @Mock private SessionChangeRepository sessionChangeRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private DeviceRepository deviceRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private LeaderboardService leaderboardService;

    @Mock
    private com.ahogek.cttserver.stats.materialization.service.DailyStatsMaterializer
            dailyStatsMaterializer;

    @Mock
    private com.ahogek.cttserver.stats.achievement.service.AchievementService achievementService;

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
                        auditLogService,
                        jdbcTemplate,
                        leaderboardService,
                        dailyStatsMaterializer,
                        achievementService);
        when(deviceRepository.findByIdAndUserId(deviceId, userId))
                .thenReturn(Optional.of(new Device()));
        when(codingSessionRepository.findAllByUserIdAndSessionUuidIn(eq(userId), any()))
                .thenReturn(List.of());
        when(sessionChangeRepository.findMaxChangeIdForUser(userId)).thenReturn(7L);
    }

    private SyncSessionDto dto(
            UUID sessionUuid, int clientVersion, Instant clientModifiedAt, boolean deleted) {
        return dto(sessionUuid, "ctt-server", "Java", clientVersion, clientModifiedAt, deleted);
    }

    private SyncSessionDto dto(
            UUID sessionUuid,
            String project,
            String lang,
            int clientVersion,
            Instant clientModifiedAt,
            boolean deleted) {
        return new SyncSessionDto(
                sessionUuid,
                project,
                lang,
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
            when(codingSessionRepository.saveAll(any()))
                    .thenAnswer(
                            inv -> {
                                List<CodingSession> sessions = inv.getArgument(0);
                                for (CodingSession s : sessions) {
                                    if (s.getId() == null) {
                                        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
                                    }
                                }
                                return sessions;
                            });

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

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());

            Object[] sessionArgs = null;
            for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
                if (sqlCaptor.getAllValues().get(i).contains("INSERT INTO coding_sessions")) {
                    sessionArgs = argsCaptor.getAllValues().get(i);
                    break;
                }
            }
            assertThat(sessionArgs).isNotNull();
            // (id, user_id, session_uuid, project_name, language, start_time, end_time,
            //  client_modified_at, client_version, server_version, updated_by_device_id,
            //  origin_device_id, is_deleted, deleted_at, created_at, updated_at)
            assertThat(sessionArgs[2]).isEqualTo(sessionUuid);
            assertThat(sessionArgs[9]).isEqualTo(1L);
            assertThat(sessionArgs[10]).isEqualTo(deviceId);
            assertThat(sessionArgs[11]).isEqualTo(deviceId);
            assertThat(sessionArgs[12]).isEqualTo(false);

            verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object[].class));

            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.SYNC_PUSH,
                            ResourceType.CODING_SESSION,
                            deviceId.toString());
            verify(leaderboardService).updateUserScores(userId);
        }

        @Test
        @DisplayName("should apply incoming state and bump version when incoming wins LWW")
        void shouldApplyIncoming_whenIncomingWinsLww() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession existing =
                    existingSession(UUID.randomUUID(), 1, Instant.parse("2026-08-25T09:30:00Z"), 5);
            existing.setSessionUuid(sessionUuid);
            when(codingSessionRepository.findAllByUserIdAndSessionUuidIn(eq(userId), any()))
                    .thenReturn(List.of(existing));

            service.push(
                    userId,
                    deviceId,
                    List.of(
                            dto(
                                    sessionUuid,
                                    "other",
                                    "Kotlin",
                                    2,
                                    Instant.parse("2026-08-25T10:00:00Z"),
                                    false)));

            assertThat(existing.getClientVersion()).isEqualTo(2);
            assertThat(existing.getClientModifiedAt())
                    .isEqualTo(Instant.parse("2026-08-25T10:00:00Z"));
            assertThat(existing.getServerVersion()).isEqualTo(6);
            assertThat(existing.isDeleted()).isFalse();
            assertThat(existing.getLanguage()).isEqualTo("Kotlin");

            verify(jdbcTemplate).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("should keep existing state when content is identical but timestamp drifted")
        void shouldKeepExisting_whenContentSame_butClientModifiedAtDrifted() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession existing =
                    existingSession(UUID.randomUUID(), 1, Instant.parse("2026-08-25T09:30:00Z"), 5);
            existing.setSessionUuid(sessionUuid);
            when(codingSessionRepository.findAllByUserIdAndSessionUuidIn(eq(userId), any()))
                    .thenReturn(List.of(existing));

            // Same project/language/times as the server row; only clientModifiedAt is later
            // (e.g. plugin full re-push with clock/timezone drift). Must be an idempotent no-op.
            service.push(
                    userId,
                    deviceId,
                    List.of(
                            dto(
                                    sessionUuid,
                                    "ctt-server",
                                    "Java",
                                    2,
                                    Instant.parse("2026-08-25T10:00:00Z"),
                                    false)));

            assertThat(existing.getClientVersion()).isEqualTo(1);
            assertThat(existing.getServerVersion()).isEqualTo(5);
            assertThat(existing.getLanguage()).isEqualTo("Java");
            verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
            verify(codingSessionRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("should keep existing state without change log when existing wins LWW")
        void shouldKeepExisting_whenExistingWinsLww() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession existing =
                    existingSession(UUID.randomUUID(), 5, Instant.parse("2026-08-25T10:00:00Z"), 5);
            existing.setSessionUuid(sessionUuid);
            when(codingSessionRepository.findAllByUserIdAndSessionUuidIn(eq(userId), any()))
                    .thenReturn(List.of(existing));

            service.push(
                    userId,
                    deviceId,
                    List.of(dto(sessionUuid, 3, Instant.parse("2026-08-25T09:00:00Z"), false)));

            assertThat(existing.getServerVersion()).isEqualTo(5);
            assertThat(existing.getClientVersion()).isEqualTo(5);
            verify(codingSessionRepository, never()).saveAll(any());
            verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("should soft-delete and log a delete when incoming delete wins LWW")
        void shouldSoftDelete_whenIncomingDeleteWinsLww() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession existing =
                    existingSession(UUID.randomUUID(), 1, Instant.parse("2026-08-25T09:30:00Z"), 5);
            existing.setSessionUuid(sessionUuid);
            when(codingSessionRepository.findAllByUserIdAndSessionUuidIn(eq(userId), any()))
                    .thenReturn(List.of(existing));

            service.push(
                    userId,
                    deviceId,
                    List.of(dto(sessionUuid, 2, Instant.parse("2026-08-25T10:00:00Z"), true)));

            assertThat(existing.isDeleted()).isTrue();
            assertThat(existing.getDeletedAt()).isNotNull();
            assertThat(existing.getServerVersion()).isEqualTo(6);

            verify(jdbcTemplate).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("should keep existing soft-deleted session when incoming is live")
        void shouldKeepExisting_whenServerAlreadySoftDeletedAndIncomingLive() {
            UUID sessionUuid = UUID.randomUUID();
            CodingSession softDeleted =
                    existingSession(UUID.randomUUID(), 1, Instant.parse("2026-08-25T09:30:00Z"), 5);
            softDeleted.setDeleted(true);
            softDeleted.setDeletedAt(Instant.parse("2026-08-25T09:45:00Z"));
            softDeleted.setSessionUuid(sessionUuid);
            when(codingSessionRepository.findAllByUserIdAndSessionUuidIn(eq(userId), any()))
                    .thenReturn(List.of(softDeleted));

            service.push(
                    userId,
                    deviceId,
                    List.of(dto(sessionUuid, 2, Instant.parse("2026-08-25T10:00:00Z"), false)));

            assertThat(softDeleted.isDeleted()).isTrue();
            assertThat(softDeleted.getServerVersion()).isEqualTo(5);
            verify(codingSessionRepository, never()).saveAll(any());
            verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("should skip creation when client deletes a session the server never had")
        void shouldSkip_whenClientDeletesSessionServerNeverHad() {
            UUID sessionUuid = UUID.randomUUID();

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
            verify(codingSessionRepository, never()).saveAll(any());
            verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
            verify(auditLogService)
                    .logSuccess(
                            userId,
                            AuditAction.SYNC_PUSH,
                            ResourceType.CODING_SESSION,
                            deviceId.toString());
        }

        @Test
        @DisplayName("should throw NotFoundException when device is revoked")
        void shouldThrowNotFoundException_whenDeviceRevoked() {
            Device revoked = new Device();
            revoked.setRevokedAt(Instant.now());
            when(deviceRepository.findByIdAndUserId(deviceId, userId))
                    .thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> service.push(userId, deviceId, List.of()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_002);
        }

        @Test
        @DisplayName("should throw NotFoundException when device is not owned by user")
        void shouldThrowNotFoundException_whenDeviceNotOwned() {
            when(deviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.push(userId, deviceId, List.of()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_002);

            verify(codingSessionRepository, never()).findAllByUserIdAndSessionUuidIn(any(), any());
            verify(auditLogService)
                    .logFailure(
                            userId,
                            AuditAction.SYNC_PUSH,
                            ResourceType.CODING_SESSION,
                            deviceId.toString(),
                            ErrorCode.COMMON_002.name());
        }

        @Test
        @DisplayName("should propagate failure so the whole batch rolls back atomically")
        void shouldPropagateFailure_whenAnySessionFails() {
            UUID sessionUuid1 = UUID.randomUUID();
            UUID sessionUuid2 = UUID.randomUUID();
            ArgumentCaptor<Object[]> updateArgs = ArgumentCaptor.forClass(Object[].class);
            when(jdbcTemplate.update(anyString(), updateArgs.capture()))
                    .thenAnswer(
                            _ -> {
                                Object[] args = updateArgs.getValue();
                                for (int i = 0; i + 2 < args.length; i += 16) {
                                    if (sessionUuid2.equals(args[i + 2])) {
                                        throw new IllegalStateException("persistence failure");
                                    }
                                }
                                return 1;
                            });

            List<SyncSessionDto> batch =
                    List.of(
                            dto(sessionUuid1, 1, Instant.parse("2026-08-25T10:00:00Z"), false),
                            dto(sessionUuid2, 1, Instant.parse("2026-08-25T10:00:00Z"), false));
            assertThatThrownBy(() -> service.push(userId, deviceId, batch))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("persistence failure");

            verify(auditLogService)
                    .logFailure(
                            userId,
                            AuditAction.SYNC_PUSH,
                            ResourceType.CODING_SESSION,
                            deviceId.toString(),
                            IllegalStateException.class.getSimpleName());
        }
    }
}
