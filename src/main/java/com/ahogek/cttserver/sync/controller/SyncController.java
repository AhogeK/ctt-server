package com.ahogek.cttserver.sync.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.security.RequiresApiKeyScope;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.sync.dto.SyncPullRequest;
import com.ahogek.cttserver.sync.dto.SyncPullResponse;
import com.ahogek.cttserver.sync.dto.SyncPushRequest;
import com.ahogek.cttserver.sync.dto.SyncPushResponse;
import com.ahogek.cttserver.sync.service.SyncPullService;
import com.ahogek.cttserver.sync.service.SyncPushService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * Bidirectional sync engine endpoints.
 *
 * <p>Provides pull and push operations for multi-device data synchronization. These endpoints
 * require API keys with the SYNC scope. JWT-authenticated users bypass scope checks.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-13
 */
@Tag(name = "Sync", description = "Bidirectional data synchronization engine")
@RestController
@RequestMapping("/api/v1/sync")
@SecurityRequirement(name = "bearerAuth")
public class SyncController {

    private static final String SCOPE_DENIED_EXAMPLE =
            """
            {
              "code": "AUTH_020",
              "message": "API key missing required scope",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 403,
              "timestamp": "2026-07-13T10:30:00Z"
            }
            """;

    private static final String UNAUTHORIZED_EXAMPLE =
            """
            {
              "code": "AUTH_010",
              "message": "API key invalid",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 401,
              "timestamp": "2026-07-13T10:30:00Z"
            }
            """;

    private static final String VALIDATION_EXAMPLE =
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
              "timestamp": "2026-07-13T10:30:00Z"
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
              "timestamp": "2026-07-13T10:30:00Z"
            }
            """;

    private static final String RATE_LIMITED_EXAMPLE =
            """
            {
              "code": "RATE_LIMIT_001",
              "message": "Too many requests",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 429,
              "timestamp": "2026-07-13T10:30:00Z",
              "retryAfter": "2026-07-13T10:31:00Z"
            }
            """;

    private final SyncPullService syncPullService;
    private final SyncPushService syncPushService;
    private final CurrentUserProvider currentUserProvider;

    public SyncController(
            SyncPullService syncPullService,
            SyncPushService syncPushService,
            CurrentUserProvider currentUserProvider) {
        this.syncPullService = syncPullService;
        this.syncPushService = syncPushService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Pull latest changes from server",
            description =
                    """
                    Retrieves changes since the client's last sync point. Requires SYNC scope on \
                    the API key. Returns a batch of changes and a new sync cursor for the next pull.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Changes retrieved successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "validationError",
                                                        summary = "Invalid request body",
                                                        value = VALIDATION_EXAMPLE))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid API key",
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
                        responseCode = "404",
                        description = "Device not found or access denied - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "deviceNotFound",
                                                        summary = "Device not owned by user",
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
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
                                                        value = RATE_LIMITED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.SYNC)
    @RateLimit(type = RateLimitType.API, limit = 120, windowSeconds = 60)
    @PostMapping("/pull")
    public ResponseEntity<RestApiResponse<SyncPullResponse>> pull(
            @Valid @RequestBody SyncPullRequest request) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        SyncPullResponse response =
                syncPullService.pull(userId, request.deviceId(), request.lastPulledChangeId());
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Push local changes to server",
            description =
                    """
                    Submits local changes for server-side processing. Requires SYNC scope on \
                    the API key. Changes are processed using LWW conflict resolution.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Changes accepted for processing",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "validationError",
                                                        summary = "Invalid request body",
                                                        value = VALIDATION_EXAMPLE))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid API key",
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
                        responseCode = "404",
                        description = "Device not found or access denied - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "deviceNotFound",
                                                        summary = "Device not owned by user",
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
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
                                                        value = RATE_LIMITED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.SYNC)
    @RateLimit(type = RateLimitType.API, limit = 120, windowSeconds = 60)
    @PostMapping("/push")
    public ResponseEntity<RestApiResponse<SyncPushResponse>> push(
            @Valid @RequestBody SyncPushRequest request) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        SyncPushResponse response =
                syncPushService.push(userId, request.deviceId(), request.sessions());
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }
}
