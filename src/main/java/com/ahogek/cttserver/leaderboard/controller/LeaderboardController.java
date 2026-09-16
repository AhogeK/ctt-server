package com.ahogek.cttserver.leaderboard.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.security.RequiresApiKeyScope;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.leaderboard.dto.LanguageBoardsResponse;
import com.ahogek.cttserver.leaderboard.dto.LeaderboardResponse;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardDimension;
import com.ahogek.cttserver.leaderboard.enums.LeaderboardPeriod;
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
import io.swagger.v3.oas.annotations.Parameter;
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
 * <p>Returns the global ranking for a dimension (total coding duration, longest consecutive
 * coding-day streak, night-owl / early-bird window duration or week-over-week growth) and time
 * window (lifetime or current week / month / year) backed by a Redis ZSet, together with the
 * calling user's rank. Requires READ scope on the API key so plugins can fetch rankings; JWT users
 * bypass scope checks.
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

    private static final String INVALID_COMBINATION_EXAMPLE =
            """
            {
              "code": "COMMON_003",
              "message": "Validation error",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 400,
              "timestamp": "2026-08-31T10:00:00Z"
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
                    "Returns the global ranking for a dimension and time window (lifetime, or the"
                            + " current week / month / year), with the calling user's rank and the"
                            + " size of the ranking."
                            + " Dimensions: TOTAL (merged coding seconds), STREAK (longest"
                            + " consecutive coding-day streak), NIGHT_OWL (merged 22:00-05:00"
                            + " duration), EARLY_BIRD (merged 06:00-09:00 duration), GROWTH (net"
                            + " growth against the immediately preceding period, may be negative)"
                            + ", ACTIVE_DAYS (distinct coding days) and LANGUAGE (merged duration"
                            + " in one language, one board per language — pass `language`, and list"
                            + " the available boards via GET /api/v1/leaderboard/languages)."
                            + " The period defaults to ALL, except for GROWTH which defaults to"
                            + " WEEK. Periods are supported by dimension: TOTAL, NIGHT_OWL,"
                            + " EARLY_BIRD, ACTIVE_DAYS and LANGUAGE accept ALL/WEEK/MONTH/YEAR;"
                            + " STREAK"
                            + " accepts ALL only (its periods are shorter than the runs it"
                            + " rewards); GROWTH accepts WEEK/MONTH/YEAR but not ALL (an"
                            + " unbounded history has nothing to grow from).")
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
                        responseCode = "400",
                        description = "Invalid dimension/period combination - COMMON_003",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "invalid-combination",
                                                        summary =
                                                                "Dimension does not support the"
                                                                        + " period",
                                                        value = INVALID_COMBINATION_EXAMPLE))),
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
            @RequestParam(name = "period", required = false) LeaderboardPeriod period,
            @RequestParam(name = "language", required = false)
                    @Parameter(
                            description =
                                    "Canonical language, required for dimension=LANGUAGE and rejected"
                                            + " for the others; see GET /api/v1/leaderboard/languages",
                            example = "Java")
                    String language,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(name = "offset", defaultValue = "0") @Min(0) int offset) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        // The dimension owns both the legal period set and the default, so an omitted period can
        // never select one the same dimension would reject.
        LeaderboardPeriod effectivePeriod = period != null ? period : dimension.defaultPeriod();
        LeaderboardResponse response =
                leaderboardService.getLeaderboard(
                        dimension, effectivePeriod, language, limit, offset, currentUser.id());
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Languages that have a leaderboard",
            description =
                    "Returns the canonical languages with at least one ranked member, so a client can"
                            + " offer a selector for dimension=LANGUAGE without guessing which boards"
                            + " exist or probing empty ones. A language is added when a user is ranked"
                            + " in it and never removed; a board may therefore be empty for a period."
                            + " `type` is the Linguist category, so a client can group or filter"
                            + " (for example to show programming languages separately from formats).")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Language boards retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                LanguageBoardsResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "Unauthenticated",
                                                        value =
                                                                "{\"code\":\"AUTH_010\","
                                                                        + "\"message\":\"API key"
                                                                        + " invalid\","
                                                                        + "\"httpStatus\":401}"))),
            })
    @RequiresApiKeyScope(ApiKeyScope.READ)
    @RateLimit(type = RateLimitType.API, limit = 60, windowSeconds = 60)
    @GetMapping("/languages")
    public ResponseEntity<RestApiResponse<LanguageBoardsResponse>> languages() {
        return ResponseEntity.ok(
                RestApiResponse.ok(
                        new LanguageBoardsResponse(leaderboardService.languageBoards())));
    }
}
