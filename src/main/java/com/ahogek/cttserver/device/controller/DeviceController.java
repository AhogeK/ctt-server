package com.ahogek.cttserver.device.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.model.ApiKeyPrincipal;
import com.ahogek.cttserver.auth.apikey.security.RequiresApiKeyScope;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.device.dto.DeviceResponse;
import com.ahogek.cttserver.device.dto.RegisterDeviceRequest;
import com.ahogek.cttserver.device.service.DeviceService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * <p>Provides endpoints for registering, listing and revoking user devices.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-28
 */
@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Device Management", description = "User device registration and session management")
public class DeviceController {

    private static final String UNAUTHORIZED_EXAMPLE =
            """
            {
              "code": "AUTH_010",
              "message": "API key invalid",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 401,
              "timestamp": "2026-08-28T10:00:00Z"
            }
            """;

    private static final String SCOPE_DENIED_EXAMPLE =
            """
            {
              "code": "AUTH_020",
              "message": "API key missing required scope",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 403,
              "timestamp": "2026-08-28T10:00:00Z"
            }
            """;

    private static final String DEVICE_NOT_FOUND_EXAMPLE =
            """
            {
              "code": "COMMON_002",
              "message": "Device not found or access denied",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 404,
              "timestamp": "2026-08-28T10:00:00Z"
            }
            """;

    private final DeviceService deviceService;
    private final CurrentUserProvider currentUserProvider;

    public DeviceController(DeviceService deviceService, CurrentUserProvider currentUserProvider) {
        this.deviceService = deviceService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Registers or updates a client device, binding the authenticating API key to it.
     *
     * <p>This is the sync prerequisite: a plugin registers its local device identifier with a
     * SYNC-scoped API key so subsequent pull/push calls pass the ownership check. When
     * authenticated via API key, the key's {@code device} reference is bound to the registered
     * device.
     *
     * @param request the device metadata to register
     * @return the registered device
     */
    @Operation(
            summary = "Register device",
            description =
                    "Registers or updates a client device owned by the authenticated user, binding the"
                            + " authenticating API key to it. Requires SYNC scope on the API key.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Device registered successfully",
                        content =
                                @Content(
                                        schema = @Schema(implementation = DeviceResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "registered",
                                                        summary = "Registered device",
                                                        value =
                                                                """
                                                                {
                                                                  "success": true,
                                                                  "message": "Operation successful",
                                                                  "data": {
                                                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                                                    "deviceName": "MacBook Pro",
                                                                    "platform": "macOS",
                                                                    "ideName": "IntelliJ IDEA",
                                                                    "ideVersion": "2026.1",
                                                                    "appVersion": "1.2.0",
                                                                    "createdAt": "2026-08-28T10:00:00Z",
                                                                    "lastSeenAt": "2026-08-28T10:00:00Z"
                                                                  },
                                                                  "timestamp": "2026-08-28T10:00:00Z"
                                                                }"""))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error - COMMON_003",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "validation-error",
                                                        summary = "Missing deviceId",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_003",
                                                                  "message": "Validation error",
                                                                  "details": [
                                                                    {
                                                                      "field": "deviceId",
                                                                      "message": "deviceId is required"
                                                                    }
                                                                  ],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-08-28T10:00:00Z"
                                                                }"""))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid API key or JWT",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid API key",
                                                        value = UNAUTHORIZED_EXAMPLE))),
                @ApiResponse(
                        responseCode = "403",
                        description = "API key missing required scope - AUTH_020",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "scope-denied",
                                                        summary = "API key lacks SYNC scope",
                                                        value = SCOPE_DENIED_EXAMPLE))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Device already registered to another user - DEVICE_001",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "conflict",
                                                        summary = "Device owned by another user",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "DEVICE_001",
                                                                  "message": "Device already registered to another user",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 409,
                                                                  "timestamp": "2026-08-28T10:00:00Z"
                                                                }"""))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Rate limit exceeded - RATE_LIMIT_001",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "rate-limited",
                                                        summary = "Too many requests",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "RATE_LIMIT_001",
                                                                  "message": "Too many requests",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 429,
                                                                  "timestamp": "2026-08-28T10:00:00Z",
                                                                  "retryAfter": "2026-08-28T11:00:00Z"
                                                                }""")))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RequiresApiKeyScope(ApiKeyScope.SYNC)
    @RateLimit(type = RateLimitType.USER, limit = 10, windowSeconds = 3600)
    @PostMapping
    public ResponseEntity<RestApiResponse<DeviceResponse>> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        DeviceResponse device =
                deviceService.registerDevice(currentUser.id(), currentApiKeyId(), request);
        return ResponseEntity.ok(RestApiResponse.ok(device));
    }

    /**
     * Lists all registered devices for the authenticated user.
     *
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
                        description = "Unauthorized - missing or invalid API key or JWT",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid API key",
                                                        value = UNAUTHORIZED_EXAMPLE))),
                @ApiResponse(
                        responseCode = "403",
                        description = "API key missing required scope - AUTH_020",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "scope-denied",
                                                        summary =
                                                                "API key lacks READ or SYNC scope",
                                                        value = SCOPE_DENIED_EXAMPLE)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RequiresApiKeyScope({ApiKeyScope.READ, ApiKeyScope.SYNC})
    @GetMapping
    public ResponseEntity<RestApiResponse<List<DeviceResponse>>> listDevices() {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        List<DeviceResponse> devices = deviceService.listUserDevices(currentUser.id());
        return ResponseEntity.ok(RestApiResponse.ok(devices));
    }

    /**
     * Revokes a specific device, terminating all its active sessions.
     *
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
                        description = "Unauthorized - missing or invalid API key or JWT",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid API key",
                                                        value = UNAUTHORIZED_EXAMPLE))),
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
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
                @ApiResponse(
                        responseCode = "403",
                        description = "API key missing required scope - AUTH_020",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "scope-denied",
                                                        summary = "API key lacks WRITE scope",
                                                        value = SCOPE_DENIED_EXAMPLE)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RequiresApiKeyScope(ApiKeyScope.WRITE)
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<RestApiResponse<Void>> revokeDevice(@PathVariable UUID deviceId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        deviceService.revokeDevice(currentUser.id(), deviceId);
        return ResponseEntity.ok(RestApiResponse.ok());
    }

    private UUID currentApiKeyId() {
        // The key id lives only on ApiKeyPrincipal; CurrentUserProvider does not expose it, so the
        // security context is read directly here. JWT callers yield null.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof ApiKeyPrincipal apiKeyPrincipal) {
            return apiKeyPrincipal.keyId();
        }
        return null;
    }
}
