package com.ahogek.cttserver.sync.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.exception.BusinessException;
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
import com.ahogek.cttserver.sync.service.ConflictResolver.Decision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    public SyncPushService(
            CodingSessionRepository codingSessionRepository,
            SessionChangeRepository sessionChangeRepository,
            DeviceRepository deviceRepository,
            AuditLogService auditLogService) {
        this.codingSessionRepository = codingSessionRepository;
        this.sessionChangeRepository = sessionChangeRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
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
            return doPush(userId, deviceId, sessions);
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

        for (SyncSessionDto dto : sessions) {
            Optional<CodingSession> existingOpt =
                    codingSessionRepository.findByUserIdAndSessionUuid(userId, dto.sessionUuid());
            if (existingOpt.isEmpty()) {
                createSession(userId, deviceId, dto);
            } else {
                applyConflict(userId, deviceId, existingOpt.get(), dto);
            }
        }

        long nextCursor = sessionChangeRepository.findMaxChangeIdForUser(userId);
        auditLogService.logSuccess(
                userId, AuditAction.SYNC_PUSH, ResourceType.CODING_SESSION, deviceId.toString());
        log.info("User {} pushed {} sessions from device {}", userId, sessions.size(), deviceId);

        return new SyncPushResponse(nextCursor);
    }

    private void createSession(UUID userId, UUID deviceId, SyncSessionDto dto) {
        if (dto.deleted()) {
            // Deleting a session the server never had is an idempotent no-op: no row, no change.
            return;
        }
        CodingSession session = new CodingSession();
        session.setUserId(userId);
        session.setSessionUuid(dto.sessionUuid());
        applyIncomingFields(session, dto);
        session.setServerVersion(1);
        session.setUpdatedByDeviceId(deviceId);
        codingSessionRepository.save(session);
        appendChange(userId, deviceId, session, ChangeOp.UPSERT);
    }

    private void applyConflict(
            UUID userId, UUID deviceId, CodingSession existing, SyncSessionDto dto) {
        CodingSession incoming = toIncomingState(dto);
        Decision decision = ConflictResolver.resolve(existing, incoming);
        switch (decision) {
            case APPLY_INCOMING -> {
                applyIncomingFields(existing, dto);
                existing.bumpServerVersion();
                existing.setUpdatedByDeviceId(deviceId);
                codingSessionRepository.save(existing);
                appendChange(userId, deviceId, existing, ChangeOp.UPSERT);
            }
            case APPLY_DELETE -> {
                existing.softDelete(Instant.now());
                existing.bumpServerVersion();
                existing.setUpdatedByDeviceId(deviceId);
                codingSessionRepository.save(existing);
                appendChange(userId, deviceId, existing, ChangeOp.DELETE);
            }
            case KEEP_EXISTING -> {
                // Server state wins; leave the row untouched (idempotent no-op).
            }
        }
    }

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

    private void appendChange(UUID userId, UUID deviceId, CodingSession session, ChangeOp op) {
        SessionChange change = new SessionChange();
        change.setUserId(userId);
        change.setDeviceId(deviceId);
        change.setSessionId(session.getId());
        change.setOp(op);
        change.setServerVersion(session.getServerVersion());
        sessionChangeRepository.save(change);
    }

    private String errorCodeName(Exception e) {
        return e instanceof BusinessException be
                ? be.errorCode().name()
                : e.getClass().getSimpleName();
    }
}
