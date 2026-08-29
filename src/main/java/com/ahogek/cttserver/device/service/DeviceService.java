package com.ahogek.cttserver.device.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.repository.ApiKeyRepository;
import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.device.dto.DeviceResponse;
import com.ahogek.cttserver.device.dto.RegisterDeviceRequest;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Device management service.
 *
 * <p>Handles device registration, listing user devices and revoking device sessions.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-28
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AuditLogService auditLogService;

    public DeviceService(
            DeviceRepository deviceRepository,
            RefreshTokenRepository refreshTokenRepository,
            ApiKeyRepository apiKeyRepository,
            AuditLogService auditLogService) {
        this.deviceRepository = deviceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Registers or updates a client device owned by the user, binding the authenticating API key to
     * it when present.
     *
     * <p>This is the sync prerequisite: a plugin registers its local device identifier so that
     * subsequent pull/push ownership checks pass. Registering an existing deviceId refreshes its
     * metadata, {@code lastSeenAt} and {@code lastIp}. When an API key is supplied, its {@code
     * device} reference is set to the registered device — a key tracks the most recently registered
     * device.
     *
     * @param userId the owning user id
     * @param keyId the authenticating API key id, or {@code null} for JWT-authenticated callers
     * @param request the device metadata to register
     * @return the registered device response
     * @throws ConflictException if the deviceId is already owned by another user
     */
    @Transactional
    public DeviceResponse registerDevice(UUID userId, UUID keyId, RegisterDeviceRequest request) {
        Device device =
                deviceRepository
                        .findById(request.deviceId())
                        .map(
                                existing -> {
                                    if (!existing.getUserId().equals(userId)) {
                                        throw new ConflictException(
                                                ErrorCode.DEVICE_001,
                                                "Device already registered to another user");
                                    }
                                    return existing;
                                })
                        .orElseGet(
                                () -> {
                                    Device created = new Device();
                                    created.setId(request.deviceId());
                                    created.setUserId(userId);
                                    return created;
                                });

        device.setDeviceName(request.deviceName());
        device.setPlatform(request.platform());
        device.setIdeName(request.ideName());
        device.setIdeVersion(request.ideVersion());
        device.setAppVersion(request.appVersion());
        device.setLastIp(RequestContext.current().map(RequestInfo::clientIp).orElse(null));
        device.setLastSeenAt(Instant.now());
        device.setRevokedAt(null);
        deviceRepository.save(device);

        if (keyId != null) {
            apiKeyRepository
                    .findByIdAndUserId(keyId, userId)
                    .ifPresent(
                            key -> {
                                key.setDevice(device);
                                apiKeyRepository.save(key);
                            });
        }

        auditLogService.logSuccess(
                userId, AuditAction.DEVICE_LINKED, ResourceType.DEVICE, device.getId().toString());
        log.info("User {} registered device {}", userId, device.getId());

        return DeviceResponse.fromEntity(device);
    }

    /**
     * Lists all devices for a user, ordered by last activity.
     *
     * @param userId the user ID
     * @return list of device responses
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> listUserDevices(UUID userId) {
        return deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId).stream()
                .map(DeviceResponse::fromEntity)
                .toList();
    }

    /**
     * Revokes a specific device by revoking all its refresh tokens.
     *
     * <p>This effectively logs out all sessions associated with the device. The device record
     * itself is not deleted to preserve audit history.
     *
     * @param userId the user ID (ownership check)
     * @param deviceId the device ID to revoke
     * @throws NotFoundException if device not found or not owned by user
     */
    @Transactional
    public void revokeDevice(UUID userId, UUID deviceId) {
        Device device =
                deviceRepository
                        .findByIdAndUserId(deviceId, userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.COMMON_002,
                                                "Device not found or access denied"));

        int revokedCount =
                refreshTokenRepository.revokeDeviceTokens(userId, deviceId, Instant.now());
        device.setRevokedAt(Instant.now());
        deviceRepository.save(device);

        log.info("User {} revoked device {} ({} tokens revoked)", userId, deviceId, revokedCount);
    }
}
