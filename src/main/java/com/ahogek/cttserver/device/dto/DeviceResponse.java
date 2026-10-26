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
        @Schema(
                        description =
                                "IDE this device most recently used (falls back to the registration"
                                        + " value when no pushed session carries one)",
                        example = "IntelliJ IDEA")
                String ideName,
        @Schema(description = "Version of that most recently used IDE", example = "2024.1")
                String ideVersion,
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
        return of(device, device.getIdeName(), device.getIdeVersion());
    }

    /**
     * Creates a DeviceResponse with an explicit IDE projection.
     *
     * <p>A device id is installation-scoped, so the registry row can only hold the IDE that
     * registered last while sessions carry the IDE that produced them. The list endpoint therefore
     * reports the most recently used IDE instead of the registration value.
     *
     * @param device the device entity
     * @param ideName the projected IDE name
     * @param ideVersion the projected IDE version
     * @return DeviceResponse DTO
     */
    public static DeviceResponse of(Device device, String ideName, String ideVersion) {
        return new DeviceResponse(
                device.getId(),
                device.getDeviceName(),
                device.getPlatform(),
                ideName,
                ideVersion,
                device.getAppVersion(),
                device.getCreatedAt(),
                device.getLastSeenAt(),
                device.getRevokedAt());
    }
}
