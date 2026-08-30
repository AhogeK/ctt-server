package com.ahogek.cttserver.leaderboard.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.security.RequiresApiKeyScope;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.leaderboard.dto.LeaderboardResponse;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.leaderboard.service.LeaderboardService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * Global leaderboard endpoint.
 *
 * <p>Returns the global ranking for a dimension (total coding duration or longest consecutive
 * coding-day streak) backed by a Redis ZSet, together with the calling user's rank. Requires READ
 * scope on the API key so plugins can fetch rankings; JWT users bypass scope checks.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Validated
@RestController
@RequestMapping("/api/v1/leaderboard")
@Tag(name = "Leaderboard", description = "Global coding activity rankings")
@SecurityRequirement(name = "bearerAuth")
public class LeaderboardController {

    private static final String UNAUTHORIZED_EXAMPLE =
            """
            {
              "code": "AUTH_010",
              "message": "API key invalid",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 401,
              "timestamp": "2026-08-31T10:00:00Z"
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
              "timestamp": "2026-08-31T10:00:00Z"
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
              "timestamp": "2026-08-31T10:00:00Z",
              "retryAfter": "2026-08-31T10:01:00Z"
            }
            """;

    private final LeaderboardService leaderboardService;
    private final CurrentUserProvider currentUserProvider;

    public LeaderboardController(
            LeaderboardService leaderboardService, CurrentUserProvider currentUserProvider) {
        this.leaderboardService = leaderboardService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Global leaderboard",
            description =
                    "Returns the global ranking for a dimension (total coding seconds or longest"
                            + " consecutive coding-day streak), with the calling user's rank.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Leaderboard retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                LeaderboardResponse.class))),
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
                                                        summary = "API key lacks READ scope",
                                                        value = SCOPE_DENIED_EXAMPLE))),
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
    @RequiresApiKeyScope(ApiKeyScope.READ)
    @RateLimit(type = RateLimitType.API, limit = 60, windowSeconds = 60)
    @GetMapping
    public ResponseEntity<RestApiResponse<LeaderboardResponse>> leaderboard(
            @RequestParam("dimension") LeaderboardDimension dimension,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(name = "offset", defaultValue = "0") @Min(0) int offset) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        LeaderboardResponse response =
                leaderboardService.getLeaderboard(dimension, limit, offset, currentUser.id());
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }
}
