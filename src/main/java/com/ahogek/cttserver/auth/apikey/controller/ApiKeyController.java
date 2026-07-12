package com.ahogek.cttserver.auth.apikey.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.apikey.dto.ApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.dto.ApiKeysResponse;
import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyRequest;
import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.security.RequiresApiKeyScope;
import com.ahogek.cttserver.auth.apikey.service.ApiKeyQueryService;
import com.ahogek.cttserver.auth.apikey.service.ApiKeyService;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
 * REST endpoints for managing the authenticated user's API keys.
 *
 * <p>Four operations are exposed:
 *
 * <ul>
 *   <li>{@code POST /} — issue a new key; raw secret is returned exactly once
 *   <li>{@code GET /} — list the caller's keys (metadata only; raw secrets never re-emitted)
 *   <li>{@code GET /{id}} — fetch a single key's metadata
 *   <li>{@code DELETE /{id}} — revoke a key
 * </ul>
 *
 * <p>All endpoints enforce BOLA protection: the caller's {@code userId} is passed to the service
 * layer, which is responsible for verifying ownership before mutating or returning the key. A key
 * that exists but belongs to another user is reported as {@code AUTH_010} (401) — identical to the
 * response when the key truly does not exist — to prevent UUID enumeration attacks.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-09
 */
@Tag(name = "API Key", description = "API key issuance, listing, retrieval, and revocation")
@RestController
@RequestMapping("/api/v1/auth/api-keys")
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyController {

    private static final String SCOPE_DENIED_EXAMPLE =
            """
            {
              "code": "AUTH_020",
              "message": "API key missing required scope",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 403,
              "timestamp": "2026-07-09T10:30:00Z"
            }
            """;

    private final ApiKeyService apiKeyService;
    private final ApiKeyQueryService apiKeyQueryService;
    private final CurrentUserProvider currentUserProvider;

    public ApiKeyController(
            ApiKeyService apiKeyService,
            ApiKeyQueryService apiKeyQueryService,
            CurrentUserProvider currentUserProvider) {
        this.apiKeyService = apiKeyService;
        this.apiKeyQueryService = apiKeyQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Create a new API key",
            description =
                    """
                    Issues a new API key for the authenticated user. The full raw key value is \
                    returned exactly once in the response and cannot be retrieved later — only \
                    the SHA-256 hash is persisted. Per-user issuance limit (default 20 active \
                    keys) is enforced server-side; exceeding the limit returns 409.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "201",
                        description = "API key created; rawKey is exposed only in this response",
                        content =
                                @Content(
                                        schema = @Schema(implementation = RestApiResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "created",
                                                        summary = "Newly issued API key",
                                                        value =
                                                                """
                                                                {
                                                                  "success": true,
                                                                  "data": {
                                                                    "rawKey": "cttak_a1b2c3d4_z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4",
                                                                    "apiKey": {
                                                                      "id": "770e8400-e29b-41d4-a716-446655440002",
                                                                      "name": "MacBook Pro — IntelliJ",
                                                                      "keyPrefix": "cttak_a1b2c3d4",
                                                                      "scopes": ["READ", "SYNC"],
                                                                      "lastUsedAt": null,
                                                                      "expiresAt": null,
                                                                      "revokedAt": null,
                                                                      "createdAt": "2026-07-09T10:30:00Z",
                                                                      "status": "ACTIVE"
                                                                    }
                                                                  },
                                                                  "timestamp": "2026-07-09T10:30:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error - COMMON_003",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "validation",
                                                        summary = "Blank name or empty scopes",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_003",
                                                                  "message": "Validation error",
                                                                  "details": [
                                                                    {
                                                                      "field": "name",
                                                                      "message": "Name must not be blank"
                                                                    }
                                                                  ],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-07-09T10:30:00Z"
                                                                }
                                                                """))),
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
                                                                  "message": "Invalid or expired JWT token",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-09T10:30:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Per-user key limit exceeded - AUTH_014",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "limit-exceeded",
                                                        summary =
                                                                "User already owns the maximum allowed"
                                                                        + " number of active keys",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_014",
                                                                  "message": "Token creation failed",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 409,
                                                                  "timestamp": "2026-07-09T10:30:00Z"
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
                                                        summary =
                                                                "API key lacks the required scope",
                                                        value = SCOPE_DENIED_EXAMPLE)))
            })
    @RateLimit(type = RateLimitType.USER, limit = 10, windowSeconds = 3600)
    @RequiresApiKeyScope(ApiKeyScope.WRITE)
    @PostMapping
    public ResponseEntity<RestApiResponse<CreateApiKeyResponse>> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        CreateApiKeyResponse response = apiKeyService.createApiKey(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/auth/api-keys/" + response.apiKey().id()))
                .body(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "List the authenticated user's API keys",
            description =
                    """
                    Returns every API key owned by the authenticated user (active, expired, and \
                    revoked). The raw key value and the SHA-256 hash are never included in the \
                    response — only the visible prefix and metadata are exposed.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "API keys retrieved successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
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
                                                                  "message": "Invalid or expired JWT token",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-09T10:30:00Z"
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
                                                        summary =
                                                                "API key lacks the required scope",
                                                        value = SCOPE_DENIED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.READ)
    @GetMapping
    public ResponseEntity<RestApiResponse<ApiKeysResponse>> listApiKeys() {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        ApiKeysResponse response = new ApiKeysResponse(apiKeyQueryService.listApiKeys(userId));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Get a single API key by id",
            description =
                    """
                    Returns the metadata of a single API key. Returns 401 (AUTH_010) if the key \
                    does not exist or is owned by a different user — the two cases are \
                    intentionally indistinguishable to prevent UUID enumeration.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "API key retrieved successfully",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Unauthorized - missing or invalid JWT, or key not accessible to"
                                        + " caller",
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
                                                                  "message": "Invalid or expired JWT token",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-09T10:30:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "API key not accessible - AUTH_010 (BOLA: same response when key"
                                        + " does not exist or belongs to another user)",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "key-not-accessible",
                                                        summary =
                                                                "Key does not exist or is not owned by"
                                                                        + " the caller",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_010",
                                                                  "message": "API key invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-09T10:30:00Z"
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
                                                        summary =
                                                                "API key lacks the required scope",
                                                        value = SCOPE_DENIED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.READ)
    @GetMapping("/{id}")
    public ResponseEntity<RestApiResponse<ApiKeyResponse>> getApiKey(@PathVariable UUID id) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        ApiKeyResponse response = apiKeyQueryService.getApiKey(userId, id);
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Revoke an API key",
            description =
                    """
                    Marks the key as revoked. The key remains in the database for audit purposes \
                    but can no longer be used for authentication. Idempotent: revoking an \
                    already-revoked key is a successful no-op. Returns 401 (AUTH_010) if the key \
                    does not exist or is owned by a different user.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "204",
                        description = "API key revoked",
                        content = @Content),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "Unauthorized - missing or invalid JWT, or key not accessible to"
                                        + " caller",
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
                                                                  "message": "Invalid or expired JWT token",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-09T10:30:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                "API key not accessible - AUTH_010 (BOLA: same response when key"
                                        + " does not exist or belongs to another user)",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "key-not-accessible",
                                                        summary =
                                                                "Key does not exist or is not owned by"
                                                                        + " the caller",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_010",
                                                                  "message": "API key invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-09T10:30:00Z"
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
                                                        summary =
                                                                "API key lacks the required scope",
                                                        value = SCOPE_DENIED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.WRITE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable UUID id) {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        apiKeyService.revokeApiKey(userId, id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
