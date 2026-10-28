package com.ahogek.cttserver.user.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.security.RequiresApiKeyScope;
import com.ahogek.cttserver.auth.dto.BatchRevokeSessionsRequest;
import com.ahogek.cttserver.auth.dto.BatchRevokeSessionsResponse;
import com.ahogek.cttserver.auth.dto.LoginSessionResponse;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.auth.service.LoginSessionService;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.ValidationException;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.leaderboard.service.LeaderboardService;
import com.ahogek.cttserver.user.dto.DeleteAccountRequest;
import com.ahogek.cttserver.user.dto.UpdateProfileRequest;
import com.ahogek.cttserver.user.dto.UserProfileResponse;
import com.ahogek.cttserver.user.service.AccountDeletionService;
import com.ahogek.cttserver.user.service.UserExportService;
import com.ahogek.cttserver.user.service.UserProfileService;
import com.ahogek.cttserver.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Current user profile REST controller.
 *
 * <p>Exposes the authenticated user's profile information. Sensitive fields (password hash, last
 * login IP, JPA @Version) are intentionally excluded from the response payload.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-01
 */
@Tag(name = "User", description = "User profile and account management")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final String UNAUTHORIZED_EXAMPLE =
            """
            {
              "code": "AUTH_003",
              "message": "Token invalid",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 401,
              "timestamp": "2026-07-01T10:00:00Z"
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
              "timestamp": "2026-07-01T10:00:00Z"
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
              "timestamp": "2026-07-01T10:00:00Z"
            }
            """;
    private final UserProfileService userProfileService;
    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final LeaderboardService leaderboardService;
    private final LoginSessionService loginSessionService;
    private final UserExportService userExportService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(
            UserProfileService userProfileService,
            UserService userService,
            AccountDeletionService accountDeletionService,
            LeaderboardService leaderboardService,
            LoginSessionService loginSessionService,
            UserExportService userExportService,
            CurrentUserProvider currentUserProvider) {
        this.userProfileService = userProfileService;
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
        this.leaderboardService = leaderboardService;
        this.loginSessionService = loginSessionService;
        this.userExportService = userExportService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Get current user profile",
            description =
                    """
                    Returns the authenticated user's profile including id, email, displayName, \
                    emailVerified, createdAt, lastLoginAt, and termsVersion. Sensitive fields \
                    such as passwordHash, lastLoginIp, and the JPA @Version are never exposed.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "User profile retrieved successfully",
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
                                                                  "code": "AUTH_003",
                                                                  "message": "Token invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<RestApiResponse<UserProfileResponse>> getCurrentUserProfile() {
        UUID userId = currentUserProvider.getCurrentUserRequired().id();
        UserProfileResponse profile = userProfileService.getCurrentUserProfile(userId);
        return ResponseEntity.ok(RestApiResponse.ok(profile));
    }

    @Operation(
            summary = "Export the current user's data",
            description =
                    """
                    Streams one JSON document holding everything the account owns. The shape is the \
                    desktop client's import format, because a file nobody can import is not much \
                    of a portability feature: `exportVersion` is `1.0`, sessions sit under \
                    `sessions` with the client's own field names, and every timestamp is written as \
                    a *local* date-time without a zone, converted with the `timezoneOffset` the \
                    caller passes — the importer parses them as local times, so a `Z` suffix would \
                    make the whole import fail. Values the client model requires but the server \
                    cannot supply (a session whose device was never registered) are written as \
                    empty strings, never null. Soft-deleted sessions are left out: the importer \
                    inserts everything as live, so exporting them would resurrect rows the user \
                    deleted. Everything else the server knows — account, devices, materialized \
                    statistics, achievements, login sessions, API key metadata and OAuth bindings \
                    — rides in `serverExport`, which the importer ignores. No credentials of any \
                    kind are included: no password, no token, no key hash.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Export document streamed as a file attachment",
                        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                @ApiResponse(
                        responseCode = "400",
                        description = "Missing or out-of-range timezoneOffset",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "badOffset",
                                                        summary =
                                                                "timezoneOffset beyond ±720 minutes",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_003",
                                                                  "message": "timezoneOffset must be within ±720 minutes",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid credentials",
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
                                                                  "code": "AUTH_003",
                                                                  "message": "Token invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "403",
                        description =
                                "Forbidden - API key without READ scope, or the account is not active",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "missingScope",
                                                    summary = "API key without READ scope",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_020",
                                                              "message": "Insufficient API key scope",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-07-01T10:00:00Z"
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "accountNotActive",
                                                    summary =
                                                            "Account deleted after the token was issued",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_022",
                                                              "message": "Account is deleted",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-07-01T10:00:00Z"
                                                            }
                                                            """)
                                        })),
                @ApiResponse(
                        responseCode = "429",
                        description = "Rate limit exceeded",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "rateLimited",
                                                        summary = "More than 3 exports in an hour",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "RATE_LIMIT_001",
                                                                  "message": "Too many requests",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 429,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RequiresApiKeyScope(ApiKeyScope.READ)
    @RateLimit(type = RateLimitType.USER, limit = 3, windowSeconds = 3600)
    @GetMapping(value = "/me/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> exportCurrentUserData(
            @Parameter(
                            description = "Caller's offset from UTC in minutes, e.g. 480 for UTC+8",
                            example = "480")
                    @RequestParam(name = "timezoneOffset")
                    int timezoneOffset) {
        if (timezoneOffset < -UserExportService.MAX_TIMEZONE_OFFSET_MINUTES
                || timezoneOffset > UserExportService.MAX_TIMEZONE_OFFSET_MINUTES) {
            throw new ValidationException(
                    ErrorCode.COMMON_003, "timezoneOffset must be within ±720 minutes");
        }

        CurrentUser currentUser = currentUserProvider.getActiveUserRequired();
        UUID userId = currentUser.id();
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(timezoneOffset * 60);
        StreamingResponseBody body =
                out -> userExportService.writeExport(userId, timezoneOffset, out);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ctt-export-" + LocalDate.now(offset) + ".json\"")
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(body);
    }

    @Operation(
            summary = "List the current user's login sessions",
            description =
                    """
                    Lists the account's active sessions — one entry per login, not per refresh: \
                    rotation continues a session, so a session groups everything one login produced. \
                    Sorted by last use, most recent first. `current` marks the session the calling \
                    access token belongs to; a call made with an API key has no session of its own, \
                    so every entry reports `current: false`. `deviceName` / `ideName` are present for \
                    device-bound (plugin) sessions only. `userAgent` is returned exactly as the \
                    client sent it — the server does not parse it.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Sessions listed",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid credentials",
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
                                                                  "code": "AUTH_003",
                                                                  "message": "Token invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Forbidden - the account is not active",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "accountNotActive",
                                                        summary =
                                                                "Account deleted after the token was issued",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_022",
                                                                  "message": "Account is deleted",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 403,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Rate limit exceeded",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "rateLimited",
                                                        summary =
                                                                "More than 60 requests in an hour",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "RATE_LIMIT_001",
                                                                  "message": "Too many requests",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 429,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit(type = RateLimitType.USER, limit = 60, windowSeconds = 3600)
    @GetMapping("/me/sessions")
    public ResponseEntity<RestApiResponse<List<LoginSessionResponse>>> listLoginSessions(
            @Parameter(description = "Maximum number of sessions to return", example = "20")
                    @RequestParam(name = "limit", defaultValue = "20")
                    int limit) {
        CurrentUser currentUser = currentUserProvider.getActiveUserRequired();
        List<LoginSessionResponse> sessions =
                loginSessionService.listSessions(currentUser.id(), currentUser.sessionId(), limit);
        return ResponseEntity.ok(RestApiResponse.ok(sessions));
    }

    @Operation(
            summary = "Revoke one login session",
            description =
                    """
                    Revokes every live token of one session, so the client holding it must sign in \
                    again. Revoking the calling session is allowed and equals a logout. A session \
                    that belongs to somebody else answers `404 COMMON_002`, exactly like one that \
                    does not exist — the endpoint never confirms that a foreign session is real. \
                    The revocation takes a session-scoped database lock, so a token rotation running \
                    at the same instant either loses to it or fails outright: a revoked session \
                    cannot be kept alive by a concurrent refresh. Access tokens already issued stay \
                    valid until they expire (15 minutes), the same window logout leaves open.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "Session revoked"),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid credentials",
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
                                                                  "code": "AUTH_003",
                                                                  "message": "Token invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "403",
                        description =
                                "Forbidden - API key without WRITE scope, or the account is not active",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "missingScope",
                                                    summary = "API key without WRITE scope",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_020",
                                                              "message": "Insufficient API key scope",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-07-01T10:00:00Z"
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "accountNotActive",
                                                    summary =
                                                            "Account deleted after the token was issued",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_022",
                                                              "message": "Account is deleted",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-07-01T10:00:00Z"
                                                            }
                                                            """)
                                        })),
                @ApiResponse(
                        responseCode = "404",
                        description = "No such session for this account",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "sessionNotFound",
                                                        summary =
                                                                "Unknown session, or one owned by another account",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_002",
                                                                  "message": "Session not found",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 404,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "429",
                        description = "Rate limit exceeded",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "rateLimited",
                                                        summary =
                                                                "More than 10 requests in an hour",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "RATE_LIMIT_001",
                                                                  "message": "Too many requests",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 429,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RequiresApiKeyScope(ApiKeyScope.WRITE)
    @RateLimit(type = RateLimitType.USER, limit = 10, windowSeconds = 3600)
    @DeleteMapping("/me/sessions/{sessionId}")
    public ResponseEntity<Void> revokeLoginSession(
            @Parameter(description = "Session identifier, as returned by the session list")
                    @PathVariable
                    UUID sessionId) {
        CurrentUser currentUser = currentUserProvider.getActiveUserRequired();
        loginSessionService.revokeSession(currentUser.id(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Revoke several login sessions",
            description =
                    """
                    Revokes each listed session that is still live and owned by the caller, and
                    reports the ids that were not: unknown, already revoked, or owned by another
                    account. A batch is deliberately partial-success, so a retry after a timeout
                    converges — ids the first attempt revoked are simply skipped. Listing the
                    calling session is allowed and equals a logout for it. At most 100 distinct ids
                    per call; duplicates are ignored.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description =
                                "Batch processed; revoked counts only the sessions this call ended",
                        content =
                                @Content(
                                        schema = @Schema(implementation = RestApiResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "partial",
                                                        summary =
                                                                "Two revoked, one already revoked",
                                                        value =
                                                                """
                                                                {
                                                                  "success": true,
                                                                  "message": "Operation successful",
                                                                  "data": {
                                                                    "revoked": 2,
                                                                    "skipped": ["9f1c8f7a-2b4e-4d5a-9c3e-1a2b3c4d5e6f"]
                                                                  },
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Empty list, a null id, or more than 100 distinct ids - COMMON_003",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "tooManySessions",
                                                        summary =
                                                                "101 distinct session ids in one call",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_003",
                                                                  "message": "At most 100 distinct sessions can be revoked in one call",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-07-01T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Missing, expired, or revoked token - AUTH_003",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalidToken",
                                                        summary =
                                                                "The access token is expired or was revoked",
                                                        value = UNAUTHORIZED_EXAMPLE))),
                @ApiResponse(
                        responseCode = "403",
                        description =
                                "API key without WRITE scope (AUTH_020) or inactive account (AUTH_022)",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "scopeDenied",
                                                        summary = "API key without WRITE scope",
                                                        value = SCOPE_DENIED_EXAMPLE))),
                @ApiResponse(
                        responseCode = "429",
                        description = "More than 10 batch calls in an hour",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "rateLimited",
                                                        summary =
                                                                "More than 10 batch calls in an hour",
                                                        value = RATE_LIMITED_EXAMPLE)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RequiresApiKeyScope(ApiKeyScope.WRITE)
    @RateLimit(type = RateLimitType.USER, limit = 10, windowSeconds = 3600)
    @PostMapping("/me/sessions/revoke")
    public ResponseEntity<RestApiResponse<BatchRevokeSessionsResponse>> revokeLoginSessions(
            @Valid @RequestBody BatchRevokeSessionsRequest request) {
        CurrentUser currentUser = currentUserProvider.getActiveUserRequired();
        return ResponseEntity.ok(
                RestApiResponse.ok(
                        loginSessionService.revokeSessions(
                                currentUser.id(), request.sessionIds())));
    }

    @Operation(
            summary = "Update the current user's profile",
            description =
                    """
                    Changes the display name of the authenticated account and returns the updated \
                    profile. The name follows the registration rule (2-50 characters; Chinese, \
                    Japanese, Korean, Latin letters, digits, underscores and hyphens). Submitting \
                    the name the account already has is a successful no-op: nothing is written and \
                    no audit event is recorded. Display names are not unique — they are a label, \
                    not an identifier.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Profile updated, current profile returned",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Display name violates the shared rule - COMMON_003",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalidDisplayName",
                                                        summary =
                                                                "Display name is not 2-50 valid characters",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "COMMON_003",
                                                                  "message": "Validation error",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 400,
                                                                  "timestamp": "2026-10-18T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid credentials",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "unauthorized",
                                                        summary = "Missing or invalid credentials",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "AUTH_003",
                                                                  "message": "Token invalid",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 401,
                                                                  "timestamp": "2026-10-18T10:00:00Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "403",
                        description =
                                "Account not active - AUTH_022, or an API key without WRITE scope - AUTH_020",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "accountNotActive",
                                                    summary = "Account is not in the active state",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_022",
                                                              "message": "Account deactivated",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-10-18T10:00:00Z"
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "missingScope",
                                                    summary = "API key lacks WRITE scope",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_020",
                                                              "message": "API key missing required scope",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-10-18T10:00:00Z"
                                                            }
                                                            """)
                                        })),
                @ApiResponse(
                        responseCode = "429",
                        description = "Rate limit exceeded - RATE_LIMIT_001",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "rateLimited",
                                                        summary = "More than 10 changes in an hour",
                                                        value =
                                                                """
                                                                {
                                                                  "code": "RATE_LIMIT_001",
                                                                  "message": "Too many requests",
                                                                  "details": [],
                                                                  "traceId": "abc-123",
                                                                  "httpStatus": 429,
                                                                  "timestamp": "2026-10-18T10:00:00Z"
                                                                }
                                                                """)))
            })
    @SecurityRequirement(name = "bearerAuth")
    @RequiresApiKeyScope(ApiKeyScope.WRITE)
    @RateLimit(type = RateLimitType.USER, limit = 10, windowSeconds = 3600)
    @PatchMapping("/me")
    public ResponseEntity<RestApiResponse<UserProfileResponse>> updateCurrentUserProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        CurrentUser currentUser = currentUserProvider.getActiveUserRequired();
        userService.updateDisplayName(currentUser.id(), request.displayName());
        // Read the projection from its single source after the write, so the profile a client uses
        // to sync local state always matches what was persisted.
        return ResponseEntity.ok(
                RestApiResponse.ok(userProfileService.getCurrentUserProfile(currentUser.id())));
    }

    @Operation(
            summary = "Delete the current user's account",
            description =
                    """
                    Deletes the authenticated user's account. Identity is anonymized, every \
                    credential is revoked, and the account is removed from the leaderboards it was \
                    ranked in. Deletion is permanent: the account cannot be recovered, and the freed \
                    email address can be registered again as a new, unrelated account. \
                    Confirmed with the account password, which accounts created through an OAuth \
                    provider do not have; for those the session is the whole of the available \
                    proof. Requires a signed-in session — an API key cannot delete the account it \
                    was issued for.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Account deleted",
                        content =
                                @Content(schema = @Schema(implementation = RestApiResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - missing or invalid JWT, or wrong password",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "invalidPassword",
                                                    summary = "Supplied password is incorrect",
                                                    value =
                                                            """
                                                            {
                                                              "code": "USER_014",
                                                              "message": "Invalid password",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 401,
                                                              "timestamp": "2026-09-17T10:00:00Z"
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "invalidToken",
                                                    summary = "Missing or invalid JWT",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_003",
                                                              "message": "Token invalid",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 401,
                                                              "timestamp": "2026-09-17T10:00:00Z"
                                                            }
                                                            """)
                                        })),
                @ApiResponse(
                        responseCode = "403",
                        description = "Password not supplied, or the caller used an API key",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "passwordRequired",
                                                    summary =
                                                            "Account has a password, none supplied",
                                                    value =
                                                            """
                                                            {
                                                              "code": "USER_013",
                                                              "message": "Password verification required",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-09-17T10:00:00Z"
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "sessionRequired",
                                                    summary = "Called with an API key",
                                                    value =
                                                            """
                                                            {
                                                              "code": "AUTH_025",
                                                              "message": "This action requires a signed-in session, not an API key",
                                                              "details": [],
                                                              "traceId": "abc-123",
                                                              "httpStatus": 403,
                                                              "timestamp": "2026-09-17T10:00:00Z"
                                                            }
                                                            """)
                                        }))
            })
    @SecurityRequirement(name = "bearerAuth")
    // Password verification is bcrypt (strength 12) — a deliberate CPU cost per request. Without a
    // limit, a stolen session or a looping client turns this endpoint into CPU exhaustion.
    @RateLimit(type = RateLimitType.USER, limit = 5, windowSeconds = 3600)
    @DeleteMapping("/me")
    public ResponseEntity<RestApiResponse<EmptyResponse>> deleteCurrentUser(
            @Valid @RequestBody DeleteAccountRequest request) {
        CurrentUser currentUser = currentUserProvider.getActiveUserRequired();
        // An API key is a credential sitting on disk in the plugin; letting one destroy the account
        // it was issued for turns a leaked key into account loss.
        if (currentUser.authType() != CurrentUser.AuthenticationType.WEB_SESSION) {
            throw new ForbiddenException(ErrorCode.AUTH_025);
        }
        accountDeletionService.deleteAccount(currentUser.id(), request.password());
        // After the transaction, never inside it: a ranking write that outlived a rollback would
        // leave a live account unranked, whereas the reverse (deleted but still ranked) is logged
        // and visible.
        leaderboardService.removeUserFromRankings(currentUser.id());
        return ResponseEntity.ok(RestApiResponse.ok(EmptyResponse.ok("Account deleted")));
    }
}
