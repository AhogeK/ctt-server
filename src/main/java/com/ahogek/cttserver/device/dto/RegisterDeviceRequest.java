package com.ahogek.cttserver.device.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for registering or updating a client device.
 *
 * <p>Device registration is the sync prerequisite: a plugin authenticates with a SYNC-scoped API
 * key and registers its local device identifier so subsequent pull/push calls pass the ownership
 * check. Registering an existing deviceId refreshes its metadata.
 *
 * @param deviceId client-generated device identifier (UUID)
 * @param deviceName human-readable device name (max 255)
 * @param platform operating system platform (max 50)
 * @param ideName IDE name (max 100)
 * @param ideVersion IDE version (max 50)
 * @param appVersion application or plugin version (max 50)
 * @author AhogeK [ahogek@gmail.com]
 */
@Schema(description = "Request payload to register or update a client device")
public record RegisterDeviceRequest(
        @Schema(
                        description = "Client-generated device identifier (UUID)",
                        example = "550e8400-e29b-41d4-a716-446655440000")
                @NotNull(message = "deviceId is required")
                UUID deviceId,
        @Schema(
                        description = "Human-readable device name",
                        example = "MacBook Pro",
                        nullable = true)
                @Size(max = 255, message = "deviceName must not exceed 255 characters")
                String deviceName,
        @Schema(description = "Operating system platform", example = "macOS", nullable = true)
                @Size(max = 50, message = "platform must not exceed 50 characters")
                String platform,
        @Schema(description = "IDE name", example = "IntelliJ IDEA", nullable = true)
                @Size(max = 100, message = "ideName must not exceed 100 characters")
                String ideName,
        @Schema(description = "IDE version", example = "2026.1", nullable = true)
                @Size(max = 50, message = "ideVersion must not exceed 50 characters")
                String ideVersion,
        @Schema(description = "Application or plugin version", example = "1.2.0", nullable = true)
                @Size(max = 50, message = "appVersion must not exceed 50 characters")
                String appVersion) {}
