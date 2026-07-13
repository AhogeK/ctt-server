package com.ahogek.cttserver.sync.controller;

import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.security.RequiresApiKeyScope;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * <p>This is a minimal implementation to demonstrate SYNC scope enforcement. The actual sync engine
 * logic (LWW conflict resolution, coding sessions, change log) will be implemented in Phase Q/R.
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
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid API key",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid API key",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_010",
                                                                  "message": "API key invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-13T10:30:00Z"
                                                                }
                                                                """))),
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
                                                        value = SCOPE_DENIED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.SYNC)
    @PostMapping("/pull")
    public ResponseEntity<RestApiResponse<String>> pull() {
        // Minimal implementation - actual sync logic in Phase Q/R
        return ResponseEntity.ok(RestApiResponse.ok("pull-ok"));
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
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid API key",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid API key",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_010",
                                                                  "message": "API key invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-13T10:30:00Z"
                                                                }
                                                                """))),
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
                                                        value = SCOPE_DENIED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.SYNC)
    @PostMapping("/push")
    public ResponseEntity<RestApiResponse<String>> push() {
        // Minimal implementation - actual sync logic in Phase Q/R
        return ResponseEntity.ok(RestApiResponse.ok("push-ok"));
    }
}
