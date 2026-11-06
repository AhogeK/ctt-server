package com.ahogek.cttserver.sync.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.config.properties.SyncProperties;
import com.ahogek.cttserver.common.exception.BusinessException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;
import com.ahogek.cttserver.sync.dto.SyncChangeDto;
import com.ahogek.cttserver.sync.dto.SyncPullResponse;
import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.entity.SessionChange;
import com.ahogek.cttserver.sync.entity.SyncCursor;
import com.ahogek.cttserver.sync.repository.CodingSessionRepository;
import com.ahogek.cttserver.sync.repository.SessionChangeRepository;
import com.ahogek.cttserver.sync.repository.SyncCursorRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pull service for the bidirectional sync engine.
 *
 * <p>Delivers the change-log entries recorded after a device's watermark, joined with the winning
 * session snapshot so the client can apply each change without a follow-up lookup. The per-device
 * cursor is advanced monotonically and concurrency-safely via {@link
 * SyncCursorRepository#advancePullWatermark}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@Service
public class SyncPullService {

    private static final Logger log = LoggerFactory.getLogger(SyncPullService.class);

    private final SessionChangeRepository sessionChangeRepository;
    private final CodingSessionRepository codingSessionRepository;
    private final SyncCursorRepository syncCursorRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogService auditLogService;
    private final int pullBatchSize;

    @Autowired
    public SyncPullService(
            SessionChangeRepository sessionChangeRepository,
            CodingSessionRepository codingSessionRepository,
            SyncCursorRepository syncCursorRepository,
            DeviceRepository deviceRepository,
            AuditLogService auditLogService,
            SyncProperties syncProperties) {
        this(
                sessionChangeRepository,
                codingSessionRepository,
                syncCursorRepository,
                deviceRepository,
                auditLogService,
                syncProperties.pullBatchSize());
    }

    SyncPullService(
            SessionChangeRepository sessionChangeRepository,
            CodingSessionRepository codingSessionRepository,
            SyncCursorRepository syncCursorRepository,
            DeviceRepository deviceRepository,
            AuditLogService auditLogService,
            int pullBatchSize) {
        this.sessionChangeRepository = sessionChangeRepository;
        this.codingSessionRepository = codingSessionRepository;
        this.syncCursorRepository = syncCursorRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
        this.pullBatchSize = pullBatchSize;
    }

    /**
     * Pulls changes recorded after the device's watermark and advances the device cursor.
     *
     * <p>The effective query cursor is the maximum of the persisted per-device watermark and the
     * client-supplied cursor, so a stale client can never rewind the watermark and a fresh device
     * resumes from the client's own last-known position. The response cursor is the highest change
     * id returned (or the effective cursor when nothing changed), and the persisted watermark is
     * advanced monotonically.
     *
     * @param userId the owning user id
     * @param deviceId the client device id
     * @param lastPulledChangeId the client's last-known change id
     * @param replayFromChangeId when non-null, re-read from this change id instead of the
     *     watermark; the watermark and the returned cursor still never regress
     * @return the changes to apply and the next pull cursor
     * @throws NotFoundException if the device is not owned by the user
     */
    @Transactional
    public SyncPullResponse pull(
            UUID userId, UUID deviceId, long lastPulledChangeId, Long replayFromChangeId) {
        try {
            return doPull(userId, deviceId, lastPulledChangeId, replayFromChangeId);
        } catch (Exception e) {
            auditLogService.logFailure(
                    userId,
                    AuditAction.SYNC_PULL,
                    ResourceType.CODING_SESSION,
                    deviceId.toString(),
                    errorCodeName(e));
            throw e;
        }
    }

    private SyncPullResponse doPull(
            UUID userId, UUID deviceId, long lastPulledChangeId, Long replayFromChangeId) {
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
        // A pull is the plugin's heartbeat: it happens every round, including rounds with nothing
        // to push. Recording it here is what keeps the device from looking abandoned after its
        // registration day.
        deviceRepository.touchLastSeen(userId, deviceId, Instant.now());

        long persistedCursor =
                syncCursorRepository
                        .findByUserIdAndDeviceId(userId, deviceId)
                        .map(SyncCursor::getLastPulledChangeId)
                        .orElse(0L);
        // A replay names where the client wants to read from, because the server advanced the
        // watermark before it handed a page over and the client may not have applied it. The
        // requested point never reads ahead of the watermark (a client cannot skip by claiming a
        // larger cursor), and it is authoritative for the read: it is the client's own position,
        // not the server's memory, that decides which changes come back.
        long queryCursor =
                replayFromChangeId != null
                        ? Math.min(replayFromChangeId, persistedCursor)
                        : Math.max(persistedCursor, lastPulledChangeId);

        // Fetch one extra row to detect a next page, then trim it: a single query answers
        // both "the page" and "is there more" without a separate count.
        List<SessionChange> fetched =
                sessionChangeRepository.findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
                        queryCursor, userId, Limit.of(pullBatchSize + 1));
        boolean hasMore = fetched.size() > pullBatchSize;
        List<SessionChange> changes = hasMore ? fetched.subList(0, pullBatchSize) : fetched;

        List<SyncChangeDto> changeDtos = toChangeDtos(changes);
        long pageLast = changes.isEmpty() ? queryCursor : changes.getLast().getChangeId();
        // The cursor answers "where does this client continue reading", so it is the end of what
        // was actually handed over. Raising it to the persisted watermark would jump a client whose
        // own position lags more than a page behind over changes it never received; the watermark's
        // monotonic guard already lives in the upsert (GREATEST), so a lower value cannot rewind
        // it.
        long nextCursor = pageLast;

        // An idle poll advances nothing, and idle polls are the majority (406-540 per device per
        // day). Re-storing the same watermark costs a row version and a WAL record while changing
        // no state, so it is skipped; a device that has never advanced simply has no cursor row,
        // which reads back as 0.
        if (nextCursor > persistedCursor) {
            syncCursorRepository.advancePullWatermark(userId, deviceId, nextCursor);
        }
        // Data-plane success goes to the application log below, not to audit_logs: a pull is the
        // plugin's heartbeat (406-540/day/device measured 2026-11-05) and success rows were 90% of
        // the table. Failures are still audited in pull()'s catch block.
        log.info(
                "User {} pulled {} changes for device {} (hasMore: {})",
                userId,
                changes.size(),
                deviceId,
                hasMore);

        return new SyncPullResponse(changeDtos, nextCursor, hasMore);
    }

    private List<SyncChangeDto> toChangeDtos(List<SessionChange> changes) {
        if (changes.isEmpty()) {
            return List.of();
        }
        Map<UUID, CodingSession> sessionsById =
                codingSessionRepository
                        .findAllByIdIn(
                                changes.stream()
                                        .map(SessionChange::getSessionId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(CodingSession::getId, Function.identity()));
        return changes.stream()
                .map(change -> toChangeDto(change, sessionsById.get(change.getSessionId())))
                .toList();
    }

    private SyncChangeDto toChangeDto(SessionChange change, CodingSession session) {
        if (session == null) {
            return new SyncChangeDto(
                    change.getChangeId(),
                    change.getSessionId(),
                    null,
                    change.getOp(),
                    change.getServerVersion(),
                    change.getHappenedAt(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    null,
                    true);
        }
        return new SyncChangeDto(
                change.getChangeId(),
                session.getId(),
                session.getSessionUuid(),
                change.getOp(),
                change.getServerVersion(),
                change.getHappenedAt(),
                session.getProjectName(),
                session.getLanguage(),
                session.getStartTime(),
                session.getEndTime(),
                session.getClientModifiedAt(),
                session.getClientVersion(),
                session.getIdeName(),
                session.getIdeVersion(),
                session.isDeleted());
    }

    private String errorCodeName(Exception e) {
        return e instanceof BusinessException be
                ? be.errorCode().name()
                : e.getClass().getSimpleName();
    }
}
