package com.ahogek.cttserver.device.dto;

import com.ahogek.cttserver.device.entity.Device;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Device response DTO for API responses.
 *
 * <p>Contains device metadata shown in the device management UI.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-28
 */
@Schema(description = "Device information response")
public record DeviceResponse(
        @Schema(
                        description = "Device unique identifier",
                        example = "550e8400-e29b-41d4-a716-446655440000")
                UUID id,
        @Schema(description = "Human-readable device name", example = "MacBook Pro")
                String deviceName,
        @Schema(description = "Operating system platform", example = "macOS") String platform,
        @Schema(description = "IDE name", example = "IntelliJ IDEA") String ideName,
        @Schema(description = "IDE version", example = "2024.1") String ideVersion,
        @Schema(description = "Application or plugin version", example = "1.2.0") String appVersion,
        @Schema(description = "Device registration timestamp", example = "2026-03-01T10:00:00Z")
                Instant createdAt,
        @Schema(description = "Last activity timestamp", example = "2026-04-28T15:30:00Z")
                Instant lastSeenAt,
        @Schema(
                        description = "Revocation timestamp; null when the device is active",
                        example = "2026-08-29T12:00:00Z",
                        nullable = true)
                Instant revokedAt) {

    /**
     * Creates a DeviceResponse from a Device entity.
     *
     * @param device the device entity
     * @return DeviceResponse DTO
     */
    public static DeviceResponse fromEntity(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getDeviceName(),
                device.getPlatform(),
                device.getIdeName(),
                device.getIdeVersion(),
                device.getAppVersion(),
                device.getCreatedAt(),
                device.getLastSeenAt(),
                device.getRevokedAt());
    }
}
