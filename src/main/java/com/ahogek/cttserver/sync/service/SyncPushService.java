package com.ahogek.cttserver.sync.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.BusinessException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.leaderboard.service.LeaderboardService;
import com.ahogek.cttserver.sync.dto.SyncPushResponse;
import com.ahogek.cttserver.sync.dto.SyncSessionDto;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.enums.ChangeOp;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.sync.repository.SessionChangeRepository;
import com.ahogek.cttserver.sync.service.ConflictResolver.Decision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Push service for the bidirectional sync engine.
 *
 * <p>Applies a batch of client-submitted session states under last-write-wins (LWW) conflict
 * resolution. The whole batch is applied in a single transaction so a failure in any session rolls
 * back every other session — partial application never happens. Each accepted write appends a
 * change-log entry so downstream devices can pull the new state.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@Service
public class SyncPushService {

    private static final Logger log = LoggerFactory.getLogger(SyncPushService.class);

    private final CodingSessionRepository codingSessionRepository;
    private final SessionChangeRepository sessionChangeRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final LeaderboardService leaderboardService;

    public SyncPushService(
            CodingSessionRepository codingSessionRepository,
            SessionChangeRepository sessionChangeRepository,
            DeviceRepository deviceRepository,
            AuditLogService auditLogService,
            JdbcTemplate jdbcTemplate,
            LeaderboardService leaderboardService) {
        this.codingSessionRepository = codingSessionRepository;
        this.sessionChangeRepository = sessionChangeRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.leaderboardService = leaderboardService;
    }

    /**
     * Applies a batch of client session states atomically.
     *
     * <p>Validates device ownership first, then routes each session through {@link
     * ConflictResolver}: new sessions are created with server version 1, incoming-wins states are
     * applied and bumped, delete-wins states are soft-deleted and bumped, and keep-existing states
     * are left untouched (no change-log entry, no version bump).
     *
     * @param userId the owning user id
     * @param deviceId the client device id that originated the changes
     * @param sessions the session states to apply
     * @return the highest change id recorded after processing
     * @throws NotFoundException if the device is not owned by the user
     */
    @Transactional
    public SyncPushResponse push(UUID userId, UUID deviceId, List<SyncSessionDto> sessions) {
        try {
            SyncPushResponse response = doPush(userId, deviceId, sessions);
            updateLeaderboard(userId);
            return response;
        } catch (Exception e) {
            auditLogService.logFailure(
                    userId,
                    AuditAction.SYNC_PUSH,
                    ResourceType.CODING_SESSION,
                    deviceId.toString(),
                    errorCodeName(e));
            throw e;
        }
    }

    private SyncPushResponse doPush(UUID userId, UUID deviceId, List<SyncSessionDto> sessions) {
        Device device =
                deviceRepository
                        .findByIdAndUserId(deviceId, userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.COMMON_002,
                                                "Device not found or access denied"));
        if (device.getRevokedAt() != null) {
            throw new NotFoundException(ErrorCode.COMMON_002, "Device not found or access denied");
        }

        List<CodingSession> existingBatch =
                codingSessionRepository.findAllByUserIdAndSessionUuidIn(
                        userId, sessions.stream().map(SyncSessionDto::sessionUuid).toList());
        Map<UUID, CodingSession> known =
                existingBatch.stream()
                        .collect(
                                Collectors.toMap(
                                        CodingSession::getSessionUuid,
                                        session -> session,
                                        (first, _) -> first));

        List<CodingSession> toCreate = new ArrayList<>();
        List<CodingSession> toUpdate = new ArrayList<>();
        List<ChangeDraft> pendingChanges = new ArrayList<>();

        for (SyncSessionDto dto : sessions) {
            CodingSession existing = known.get(dto.sessionUuid());
            if (existing == null) {
                // Deleting a session the server never had is an idempotent no-op.
                if (dto.deleted()) {
                    continue;
                }
                CodingSession session = new CodingSession();
                session.setUserId(userId);
                session.setSessionUuid(dto.sessionUuid());
                applyIncomingFields(session, dto);
                session.setServerVersion(1);
                session.setUpdatedByDeviceId(deviceId);
                toCreate.add(session);
                known.put(dto.sessionUuid(), session);
                pendingChanges.add(new ChangeDraft(session, ChangeOp.UPSERT));
                continue;
            }
            CodingSession incoming = toIncomingState(dto);
            Decision decision = ConflictResolver.resolve(existing, incoming);
            switch (decision) {
                case APPLY_INCOMING -> {
                    applyIncomingFields(existing, dto);
                    existing.bumpServerVersion();
                    existing.setUpdatedByDeviceId(deviceId);
                    toUpdate.add(existing);
                    pendingChanges.add(new ChangeDraft(existing, ChangeOp.UPSERT));
                }
                case APPLY_DELETE -> {
                    existing.softDelete(Instant.now());
                    existing.bumpServerVersion();
                    existing.setUpdatedByDeviceId(deviceId);
                    toUpdate.add(existing);
                    pendingChanges.add(new ChangeDraft(existing, ChangeOp.DELETE));
                }
                case KEEP_EXISTING -> {
                    // Server state wins; leave the row untouched (idempotent no-op).
                }
            }
        }

        if (!toCreate.isEmpty()) {
            // Hand-written multi-row INSERT: Hibernate saveAll (even with
            // hibernate.jdbc.batch_size)
            // and PG's reWriteBatchedInserts both fall back to one single-row statement per row
            // for these statements, so issue one real multi-row INSERT per table instead.
            batchInsertSessions(userId, deviceId, toCreate);
        }
        if (!toUpdate.isEmpty()) {
            codingSessionRepository.saveAll(toUpdate);
            codingSessionRepository.flush();
        }
        if (!pendingChanges.isEmpty()) {
            batchInsertChanges(userId, deviceId, pendingChanges);
        }

        long nextCursor = sessionChangeRepository.findMaxChangeIdForUser(userId);
        auditLogService.logSuccess(
                userId, AuditAction.SYNC_PUSH, ResourceType.CODING_SESSION, deviceId.toString());
        log.info(
                "User {} pushed {} sessions from device {} ({} created, {} updated, {} changes)",
                userId,
                sessions.size(),
                deviceId,
                toCreate.size(),
                toUpdate.size(),
                pendingChanges.size());

        return new SyncPushResponse(nextCursor);
    }

    /**
     * Recomputes the pushed user's leaderboard scores so the global ranking reflects the new
     * sessions immediately. {@link LeaderboardService#updateUserScore} is failure-tolerant and the
     * next push self-heals, so a transient Redis issue never rolls back the push.
     */
    private void updateLeaderboard(UUID userId) {
        leaderboardService.updateUserScore(userId, LeaderboardDimension.TOTAL);
        leaderboardService.updateUserScore(userId, LeaderboardDimension.STREAK);
    }

    /** A session mutation awaiting persistence together with its change-log entry. */
    private void batchInsertSessions(UUID userId, UUID deviceId, List<CodingSession> sessions) {
        String sql =
                """
                INSERT INTO coding_sessions
                    (id, user_id, session_uuid, project_name, language, start_time,
                     end_time, client_modified_at, client_version, server_version,
                     updated_by_device_id, is_deleted, deleted_at, created_at, updated_at)
                VALUES\s"""
                        + buildValuesClause(sessions.size(), 15);
        List<Object> args = new ArrayList<>(sessions.size() * 15);
        Instant now = Instant.now();
        for (CodingSession session : sessions) {
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            args.add(session.getId());
            args.add(userId);
            args.add(session.getSessionUuid());
            args.add(session.getProjectName());
            args.add(session.getLanguage());
            args.add(session.getStartTime().atOffset(ZoneOffset.UTC));
            args.add(session.getEndTime().atOffset(ZoneOffset.UTC));
            args.add(session.getClientModifiedAt().atOffset(ZoneOffset.UTC));
            args.add(session.getClientVersion());
            args.add(session.getServerVersion());
            args.add(deviceId);
            args.add(session.isDeleted());
            args.add(
                    session.getDeletedAt() != null
                            ? session.getDeletedAt().atOffset(ZoneOffset.UTC)
                            : null);
            args.add(now.atOffset(ZoneOffset.UTC));
            args.add(now.atOffset(ZoneOffset.UTC));
        }
        log.info(
                "Bulk inserted {} coding_sessions for user {} via a single multi-row INSERT",
                sessions.size(),
                userId);
        log.debug("Bulk coding_sessions multi-row INSERT: {}", sql);
        jdbcTemplate.update(sql, args.toArray());
    }

    private void batchInsertChanges(UUID userId, UUID deviceId, List<ChangeDraft> drafts) {
        String sql =
                """
                INSERT INTO session_changes
                    (user_id, device_id, session_id, op, server_version)
                VALUES\s"""
                        + buildValuesClause(drafts.size(), 5);
        List<Object> args = new ArrayList<>(drafts.size() * 5);
        for (ChangeDraft draft : drafts) {
            args.add(userId);
            args.add(deviceId);
            args.add(draft.session().getId());
            args.add(draft.op().name());
            args.add(draft.session().getServerVersion());
        }
        log.info(
                "Bulk inserted {} session_changes for user {} via a single multi-row INSERT",
                drafts.size(),
                userId);
        log.debug("Bulk session_changes multi-row INSERT: {}", sql);
        jdbcTemplate.update(sql, args.toArray());
    }

    /**
     * Builds the {@code (?, ?, ...), (?, ?, ...)} VALUES clause for a multi-row INSERT.
     *
     * <p>The SQL text is a fixed template — table and column names are hard-coded constants, the
     * only variable part is the repeated parameter placeholder groups. Every value is bound via
     * prepared-statement parameters (never concatenated into the SQL text), so there is no SQL
     * injection surface.
     *
     * @param rowCount the number of rows to insert
     * @param columnsPerRow the column count of the target table
     * @return the VALUES clause, e.g. {@code (?, ?), (?, ?)}
     */
    private static String buildValuesClause(int rowCount, int columnsPerRow) {
        StringBuilder values = new StringBuilder();
        for (int row = 0; row < rowCount; row++) {
            if (row > 0) {
                values.append(", ");
            }
            values.append("(");
            for (int column = 0; column < columnsPerRow; column++) {
                if (column > 0) {
                    values.append(", ");
                }
                values.append("?");
            }
            values.append(")");
        }
        return values.toString();
    }

    /** A session mutation awaiting persistence together with its change-log entry. */
    private record ChangeDraft(CodingSession session, ChangeOp op) {}

    private CodingSession toIncomingState(SyncSessionDto dto) {
        CodingSession incoming = new CodingSession();
        applyIncomingFields(incoming, dto);
        incoming.setSessionUuid(dto.sessionUuid());
        incoming.setDeleted(dto.deleted());
        return incoming;
    }

    private void applyIncomingFields(CodingSession session, SyncSessionDto dto) {
        session.setProjectName(dto.projectName());
        session.setLanguage(dto.language());
        session.setStartTime(dto.startTime());
        session.setEndTime(dto.endTime());
        session.setClientModifiedAt(dto.clientModifiedAt());
        session.setClientVersion(dto.clientVersion());
    }

    private String errorCodeName(Exception e) {
        return e instanceof BusinessException be
                ? be.errorCode().name()
                : e.getClass().getSimpleName();
    }
}
