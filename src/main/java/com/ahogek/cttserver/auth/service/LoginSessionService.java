package com.ahogek.cttserver.auth.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.dto.BatchRevokeSessionsResponse;
import com.ahogek.cttserver.auth.dto.LoginSessionResponse;
import com.ahogek.cttserver.auth.entity.RefreshToken;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository.SessionRow;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lists and revokes the login sessions of an account.
 *
 * <p>A session is one login and the chain of refresh tokens rotation produced from it. Revocation
 * is serialized against rotation through a session-scoped database lock, so a session that is being
 * revoked cannot be kept alive by a refresh that started at the same moment.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-10-19
 */
@Service
public class LoginSessionService {

    /** Sessions a caller may ask for in one page, and the largest limit the endpoint honours. */
    private static final int MAX_SESSIONS = 100;

    /** Largest number of distinct sessions one batch revocation may carry. */
    private static final int MAX_BATCH_REVOCATIONS = 100;

    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogService auditLogService;

    public LoginSessionService(
            RefreshTokenRepository refreshTokenRepository,
            DeviceRepository deviceRepository,
            AuditLogService auditLogService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Lists the account's active sessions, most recently used first.
     *
     * @param userId the account whose sessions are listed
     * @param currentSessionId the session of the calling access token, null for an API key
     * @param limit the requested page size; clamped to 1..100
     * @return one entry per active session
     */
    @Transactional(readOnly = true)
    public List<LoginSessionResponse> listSessions(UUID userId, UUID currentSessionId, int limit) {
        List<SessionRow> rows = refreshTokenRepository.findActiveSessionRows(userId, Instant.now());
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, Device> devices = devicesById(rows);
        Comparator<SessionRow> recentlyUsedFirst =
                Comparator.comparing(
                                SessionRow::getLastUsedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SessionRow::getCreatedAt, Comparator.reverseOrder());

        return rows.stream()
                .sorted(recentlyUsedFirst)
                .limit(Math.min(Math.max(limit, 1), MAX_SESSIONS))
                .map(row -> toResponse(row, deviceOf(devices, row), currentSessionId))
                .toList();
    }

    /**
     * A browser session has no device, and {@link #devicesById} can return {@link Map#of()}, whose
     * {@code get(null)} throws; the absence is resolved before the lookup.
     */
    private static Device deviceOf(Map<UUID, Device> devices, SessionRow row) {
        UUID deviceId = row.getDeviceId();
        return deviceId == null ? null : devices.get(deviceId);
    }

    /**
     * Revokes one session: every live token it still holds.
     *
     * <p>The session-scoped lock is taken before the live row is read, so a concurrent refresh
     * either completes first (and its new token is revoked too) or reads the revoked state and
     * fails — the session cannot come back to life.
     *
     * @param userId the account performing the revocation
     * @param sessionId the session to revoke
     * @throws NotFoundException when the account has no live session with that id
     */
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        RefreshToken liveToken =
                findLiveTokenUnderLock(userId, sessionId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.COMMON_002, "Session not found"));

        revokeLiveSession(userId, sessionId, liveToken, Instant.now());
    }

    /**
     * Revokes several sessions in one call.
     *
     * <p>Partial success is the contract: an id that is unknown, already revoked, or owned by
     * another account is reported as skipped instead of failing the call, so a retry after a
     * timeout converges — the sessions the first attempt revoked are simply skipped.
     *
     * <p>Sessions are locked in ascending id order, so two batches that overlap walk the shared ids
     * in the same sequence. The order is what keeps them from waiting on each other; only a hash
     * collision inside the advisory lock could order two ids differently.
     *
     * @param userId the account performing the revocation
     * @param sessionIds the requested session ids; duplicates are ignored
     * @return how many sessions were revoked and which ids were skipped
     * @throws ValidationException when more than {@value #MAX_BATCH_REVOCATIONS} distinct ids are
     *     requested
     */
    @Transactional
    public BatchRevokeSessionsResponse revokeSessions(UUID userId, List<UUID> sessionIds) {
        List<UUID> distinct = sessionIds.stream().distinct().sorted().toList();
        if (distinct.size() > MAX_BATCH_REVOCATIONS) {
            throw new ValidationException(
                    ErrorCode.COMMON_003,
                    "At most "
                            + MAX_BATCH_REVOCATIONS
                            + " distinct sessions can be revoked in one call");
        }

        Instant now = Instant.now();
        int revoked = 0;
        List<UUID> skipped = new ArrayList<>();
        for (UUID sessionId : distinct) {
            Optional<RefreshToken> liveToken = findLiveTokenUnderLock(userId, sessionId);
            if (liveToken.isEmpty()) {
                skipped.add(sessionId);
                continue;
            }
            revokeLiveSession(userId, sessionId, liveToken.get(), now);
            revoked++;
        }
        return new BatchRevokeSessionsResponse(revoked, skipped);
    }

    /**
     * Takes the session lock and reads its live token in one step.
     *
     * <p>Both writers of a session — rotation and revocation — must lock before they read: a read
     * that happens first can act on a state a concurrent revocation is about to change.
     */
    private Optional<RefreshToken> findLiveTokenUnderLock(UUID userId, UUID sessionId) {
        refreshTokenRepository.lockSession(sessionId);
        return refreshTokenRepository
                .findFirstByUserIdAndSessionIdAndRevokedAtIsNullOrderByCreatedAtDesc(
                        userId, sessionId);
    }

    private void revokeLiveSession(
            UUID userId, UUID sessionId, RefreshToken liveToken, Instant now) {
        refreshTokenRepository.revokeSession(userId, sessionId, now);
        auditLogService.log(
                userId,
                AuditAction.SESSION_REVOKED,
                ResourceType.REFRESH_TOKEN,
                sessionId.toString(),
                SecuritySeverity.INFO,
                AuditDetails.extension(Map.of("kind", liveToken.getIssuedFor())));
    }

    private Map<UUID, Device> devicesById(List<SessionRow> rows) {
        List<UUID> deviceIds =
                rows.stream().map(SessionRow::getDeviceId).filter(Objects::nonNull).toList();
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        return deviceRepository.findAllById(deviceIds).stream()
                .collect(Collectors.toMap(Device::getId, device -> device));
    }

    private LoginSessionResponse toResponse(SessionRow row, Device device, UUID currentSessionId) {
        return new LoginSessionResponse(
                row.getSessionId(),
                row.getIssuedFor(),
                device != null ? device.getDeviceName() : null,
                device != null ? device.getIdeName() : null,
                row.getUserAgent(),
                row.getCreatedAt(),
                row.getLastUsedAt(),
                row.getExpiresAt(),
                row.getSessionId().equals(currentSessionId));
    }
}
