package com.ahogek.cttserver.device.controller;

import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.device.dto.DeviceResponse;
import com.ahogek.cttserver.device.service.DeviceService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Device management controller.
 *
 * <p>Provides endpoints for listing and revoking user devices.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-28
 */
@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Device Management", description = "User device registration and session management")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * Lists all registered devices for the authenticated user.
     *
     * @param currentUser current authenticated user
     * @return list of device responses ordered by last activity
     */
    @Operation(
            summary = "List user devices",
            description =
                    "Returns all registered devices for the authenticated user, ordered by last activity time")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "List of user devices",
                        content =
                                @Content(
                                        schema = @Schema(implementation = DeviceResponse[].class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid JWT",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid JWT",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_002",
                                                                  "message": "Invalid or expired JWT token"
                                                                }""")))
            })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<RestApiResponse<List<DeviceResponse>>> listDevices(
            @AuthenticationPrincipal CurrentUser currentUser) {

        List<DeviceResponse> devices = deviceService.listUserDevices(currentUser.id());

        return ResponseEntity.ok(RestApiResponse.ok(devices));
    }

    /**
     * Revokes a specific device, terminating all its active sessions.
     *
     * @param currentUser current authenticated user
     * @param deviceId the device ID to revoke
     */
    @Operation(
            summary = "Revoke device",
            description =
                    "Revokes all active sessions for a specific device. The device record is preserved for audit purposes.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Device revoked successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid JWT",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Device not found or access denied - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "not-found",
                                                        summary = "Device not found",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_002",
                                                                  "message": "Device not found or access denied"
                                                                }""")))
            })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<RestApiResponse<Void>> revokeDevice(
            @AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID deviceId) {

        deviceService.revokeDevice(currentUser.id(), deviceId);

        return ResponseEntity.ok(RestApiResponse.ok());
    }
}
