package com.ahogek.cttserver.device.service;

import com.ahogek.cttserver.auth.repository.RefreshTokenRepository;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.device.dto.DeviceResponse;
import com.ahogek.cttserver.device.entity.Device;
import com.ahogek.cttserver.device.repository.DeviceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Device management service.
 *
 * <p>Handles listing user devices and revoking device sessions.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-28
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public DeviceService(
            DeviceRepository deviceRepository, RefreshTokenRepository refreshTokenRepository) {
        this.deviceRepository = deviceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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
                .collect(Collectors.toList());
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

        log.info("User {} revoked device {} ({} tokens revoked)", userId, deviceId, revokedCount);
    }
}
