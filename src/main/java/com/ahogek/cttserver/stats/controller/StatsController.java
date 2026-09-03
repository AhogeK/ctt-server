package com.ahogek.cttserver.stats.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.security.RequiresApiKeyScope;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;
import com.ahogek.cttserver.stats.achievement.dto.AchievementResponse;
import com.ahogek.cttserver.stats.achievement.service.AchievementService;
import com.ahogek.cttserver.stats.dto.DistributionResponse;
import com.ahogek.cttserver.stats.dto.HeatmapResponse;
import com.ahogek.cttserver.stats.dto.HourlyDistributionResponse;
import com.ahogek.cttserver.stats.dto.RecentSessionResponse;
import com.ahogek.cttserver.stats.dto.StatsSummaryResponse;
import com.ahogek.cttserver.stats.dto.StreakStatsResponse;
import com.ahogek.cttserver.stats.dto.WeekHourDistributionResponse;
import com.ahogek.cttserver.stats.enums.DistributionType;
import com.ahogek.cttserver.stats.service.StatsService;
import com.ahogek.cttserver.stats.service.StatsService.SessionFilter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 * Coding statistics endpoints.
 *
 * <p>Aggregates the authenticated user's coding sessions into dashboard dimensions (summary, daily
 * heatmap, streaks, distributions, hourly usage, recent activity). Requires READ scope on the API
 * key so plugins can fetch cloud statistics; JWT users bypass scope checks.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-30
 */
@Validated
@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Statistics", description = "Coding activity statistics and analytics")
@SecurityRequirement(name = "bearerAuth")
public class StatsController {

    private static final String UNAUTHORIZED_EXAMPLE =
            """
            {
              "code": "AUTH_010",
              "message": "API key invalid",
              "details": [],
              "traceId": "abc-123",
              "httpStatus": 401,
              "timestamp": "2026-08-30T10:00:00Z"
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
              "timestamp": "2026-08-30T10:00:00Z"
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
              "timestamp": "2026-08-30T10:00:00Z",
              "retryAfter": "2026-08-30T10:01:00Z"
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
              "timestamp": "2026-08-30T10:00:00Z"
            }
            """;

    private final StatsService statsService;
    private final AchievementService achievementService;
    private final CurrentUserProvider currentUserProvider;

    public StatsController(
            StatsService statsService,
            AchievementService achievementService,
            CurrentUserProvider currentUserProvider) {
        this.statsService = statsService;
        this.achievementService = achievementService;
        this.currentUserProvider = currentUserProvider;
    }

    @Operation(
            summary = "Coding activity summary",
            description =
                    "Returns today / daily average / this week / this month / this year / lifetime"
                            + " totals in seconds, computed in the requested timezone.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "404",
                        description =
                                "Requested deviceId unknown or owned by another user - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "device-not-found",
                                                        summary =
                                                                "Device not found or access denied",
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
                @ApiResponse(
                        responseCode = "200",
                        description = "Summary retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                StatsSummaryResponse.class))),
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
    @GetMapping("/summary")
    public ResponseEntity<RestApiResponse<StatsSummaryResponse>> summary(
            @RequestParam(name = "timezoneOffset", defaultValue = "0") @Min(-720) @Max(720)
                    int timezoneOffset,
            @Parameter(
                            name = "deviceId",
                            description =
                                    "Optional origin-device filter; omitted aggregates all devices. Unknown or"
                                            + " foreign devices yield 404 COMMON_002.")
                    @RequestParam(name = "deviceId", required = false)
                    UUID deviceId,
            @Parameter(
                            name = "ideName",
                            description =
                                    "Optional exact IDE-name filter against the device registry; omitted"
                                            + " aggregates all sessions. Mutually exclusive with deviceId;"
                                            + " unknown IDE names yield 404 COMMON_002.")
                    @RequestParam(name = "ideName", required = false)
                    String ideName) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        StatsSummaryResponse response =
                statsService.summary(
                        currentUser.id(),
                        zoneOffset(timezoneOffset),
                        new SessionFilter(deviceId, ideName));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Daily coding heatmap",
            description =
                    "Returns per-day coding seconds over the inclusive date range (default: this"
                            + " calendar year) in the requested timezone.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "404",
                        description =
                                "Requested deviceId unknown or owned by another user - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "device-not-found",
                                                        summary =
                                                                "Device not found or access denied",
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
                @ApiResponse(
                        responseCode = "200",
                        description = "Heatmap retrieved",
                        content =
                                @Content(schema = @Schema(implementation = HeatmapResponse.class))),
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
    @GetMapping("/heatmap")
    public ResponseEntity<RestApiResponse<HeatmapResponse>> heatmap(
            @RequestParam(name = "timezoneOffset", defaultValue = "0") @Min(-720) @Max(720)
                    int timezoneOffset,
            @RequestParam(name = "start", required = false) LocalDate start,
            @RequestParam(name = "end", required = false) LocalDate end,
            @Parameter(
                            name = "deviceId",
                            description =
                                    "Optional origin-device filter; omitted aggregates all devices. Unknown or"
                                            + " foreign devices yield 404 COMMON_002.")
                    @RequestParam(name = "deviceId", required = false)
                    UUID deviceId,
            @Parameter(
                            name = "ideName",
                            description =
                                    "Optional exact IDE-name filter against the device registry; omitted"
                                            + " aggregates all sessions. Mutually exclusive with deviceId;"
                                            + " unknown IDE names yield 404 COMMON_002.")
                    @RequestParam(name = "ideName", required = false)
                    String ideName) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        ZoneOffset zone = zoneOffset(timezoneOffset);
        LocalDate today = LocalDate.now(zone);
        LocalDate startDate = start != null ? start : today.withDayOfYear(1);
        LocalDate endDate = end != null ? end : today;
        HeatmapResponse response =
                statsService.heatmap(
                        currentUser.id(),
                        zone,
                        startDate,
                        endDate,
                        new SessionFilter(deviceId, ideName));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Coding streaks",
            description =
                    "Returns the current and longest consecutive coding day streaks in the requested"
                            + " timezone.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "404",
                        description =
                                "Requested deviceId unknown or owned by another user - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "device-not-found",
                                                        summary =
                                                                "Device not found or access denied",
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
                @ApiResponse(
                        responseCode = "200",
                        description = "Streaks retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                StreakStatsResponse.class))),
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
    @GetMapping("/streaks")
    public ResponseEntity<RestApiResponse<StreakStatsResponse>> streaks(
            @RequestParam(name = "timezoneOffset", defaultValue = "0") @Min(-720) @Max(720)
                    int timezoneOffset,
            @Parameter(
                            name = "deviceId",
                            description =
                                    "Optional origin-device filter; omitted aggregates all devices. Unknown or"
                                            + " foreign devices yield 404 COMMON_002.")
                    @RequestParam(name = "deviceId", required = false)
                    UUID deviceId,
            @Parameter(
                            name = "ideName",
                            description =
                                    "Optional exact IDE-name filter against the device registry; omitted"
                                            + " aggregates all sessions. Mutually exclusive with deviceId;"
                                            + " unknown IDE names yield 404 COMMON_002.")
                    @RequestParam(name = "ideName", required = false)
                    String ideName) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        StreakStatsResponse response =
                statsService.streaks(
                        currentUser.id(),
                        zoneOffset(timezoneOffset),
                        new SessionFilter(deviceId, ideName));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Coding duration distribution",
            description =
                    "Returns a duration distribution by languages, projects, time-of-day, weekday,"
                            + " device or IDE, ordered by duration descending.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "404",
                        description =
                                "Requested deviceId unknown or owned by another user - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "device-not-found",
                                                        summary =
                                                                "Device not found or access denied",
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
                @ApiResponse(
                        responseCode = "200",
                        description = "Distribution retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                DistributionResponse.class))),
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
    @GetMapping("/distribution")
    public ResponseEntity<RestApiResponse<DistributionResponse>> distribution(
            @RequestParam("type") DistributionType type,
            @RequestParam(name = "timezoneOffset", defaultValue = "0") @Min(-720) @Max(720)
                    int timezoneOffset,
            @Parameter(
                            name = "deviceId",
                            description =
                                    "Optional origin-device filter; omitted aggregates all devices. Unknown or"
                                            + " foreign devices yield 404 COMMON_002.")
                    @RequestParam(name = "deviceId", required = false)
                    UUID deviceId,
            @Parameter(
                            name = "ideName",
                            description =
                                    "Optional exact IDE-name filter against the device registry; omitted"
                                            + " aggregates all sessions. Mutually exclusive with deviceId;"
                                            + " unknown IDE names yield 404 COMMON_002.")
                    @RequestParam(name = "ideName", required = false)
                    String ideName) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        DistributionResponse response =
                statsService.distribution(
                        currentUser.id(),
                        zoneOffset(timezoneOffset),
                        type,
                        new SessionFilter(deviceId, ideName));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Hourly coding usage",
            description =
                    "Returns per-hour average coding seconds across active days in the requested"
                            + " timezone.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "404",
                        description =
                                "Requested deviceId unknown or owned by another user - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "device-not-found",
                                                        summary =
                                                                "Device not found or access denied",
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
                @ApiResponse(
                        responseCode = "200",
                        description = "Hourly distribution retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                HourlyDistributionResponse.class))),
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
    @GetMapping("/hourly")
    public ResponseEntity<RestApiResponse<HourlyDistributionResponse>> hourly(
            @RequestParam(name = "timezoneOffset", defaultValue = "0") @Min(-720) @Max(720)
                    int timezoneOffset,
            @Parameter(
                            name = "deviceId",
                            description =
                                    "Optional origin-device filter; omitted aggregates all devices. Unknown or"
                                            + " foreign devices yield 404 COMMON_002.")
                    @RequestParam(name = "deviceId", required = false)
                    UUID deviceId,
            @Parameter(
                            name = "ideName",
                            description =
                                    "Optional exact IDE-name filter against the device registry; omitted"
                                            + " aggregates all sessions. Mutually exclusive with deviceId;"
                                            + " unknown IDE names yield 404 COMMON_002.")
                    @RequestParam(name = "ideName", required = false)
                    String ideName) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        HourlyDistributionResponse response =
                statsService.hourly(
                        currentUser.id(),
                        zoneOffset(timezoneOffset),
                        new SessionFilter(deviceId, ideName));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Weekly coding activity by hour",
            description =
                    "Returns the weekly coding heatmap: average seconds per weekday-hour cell in"
                            + " the requested timezone. Sessions crossing hour or day boundaries are"
                            + " sliced accordingly. Optional start/end dates (inclusive) clip the"
                            + " aggregation window; omitted bounds aggregate the full history."
                            + " Only exercised cells are returned; clients render missing cells as"
                            + " zero.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Weekly heatmap retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                WeekHourDistributionResponse
                                                                        .class))),
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
                        responseCode = "404",
                        description =
                                "Requested deviceId unknown or owned by another user - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "device-not-found",
                                                        summary =
                                                                "Device not found or access denied",
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
    @RequiresApiKeyScope(ApiKeyScope.READ)
    @RateLimit(type = RateLimitType.API, limit = 60, windowSeconds = 60)
    @GetMapping("/week-hour")
    public ResponseEntity<RestApiResponse<WeekHourDistributionResponse>> weekHour(
            @RequestParam(name = "timezoneOffset", defaultValue = "0") @Min(-720) @Max(720)
                    int timezoneOffset,
            @Parameter(
                            name = "start",
                            description =
                                    "Optional window start date (inclusive, ISO yyyy-MM-dd);"
                                            + " omitted aggregates the full history")
                    @RequestParam(name = "start", required = false)
                    LocalDate start,
            @Parameter(
                            name = "end",
                            description =
                                    "Optional window end date (inclusive, ISO yyyy-MM-dd);"
                                            + " omitted aggregates the full history")
                    @RequestParam(name = "end", required = false)
                    LocalDate end,
            @Parameter(
                            name = "deviceId",
                            description =
                                    "Optional origin-device filter; omitted aggregates all devices. Unknown or"
                                            + " foreign devices yield 404 COMMON_002.")
                    @RequestParam(name = "deviceId", required = false)
                    UUID deviceId,
            @Parameter(
                            name = "ideName",
                            description =
                                    "Optional exact IDE-name filter against the device registry; omitted"
                                            + " aggregates all sessions. Mutually exclusive with deviceId;"
                                            + " unknown IDE names yield 404 COMMON_002.")
                    @RequestParam(name = "ideName", required = false)
                    String ideName) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        WeekHourDistributionResponse response =
                statsService.weekHour(
                        currentUser.id(),
                        zoneOffset(timezoneOffset),
                        start,
                        end,
                        new SessionFilter(deviceId, ideName));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Recent coding sessions",
            description =
                    "Returns the most recent coding sessions, ordered by start time descending.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "404",
                        description =
                                "Requested deviceId unknown or owned by another user - COMMON_002",
                        content =
                                @Content(
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "device-not-found",
                                                        summary =
                                                                "Device not found or access denied",
                                                        value = DEVICE_NOT_FOUND_EXAMPLE))),
                @ApiResponse(
                        responseCode = "200",
                        description = "Recent sessions retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                RecentSessionResponse[].class))),
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
    @GetMapping("/recent")
    public ResponseEntity<RestApiResponse<List<RecentSessionResponse>>> recent(
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(
                            name = "deviceId",
                            description =
                                    "Optional origin-device filter; omitted aggregates all devices. Unknown or"
                                            + " foreign devices yield 404 COMMON_002.")
                    @RequestParam(name = "deviceId", required = false)
                    UUID deviceId,
            @Parameter(
                            name = "ideName",
                            description =
                                    "Optional exact IDE-name filter against the device registry; omitted"
                                            + " aggregates all sessions. Mutually exclusive with deviceId;"
                                            + " unknown IDE names yield 404 COMMON_002.")
                    @RequestParam(name = "ideName", required = false)
                    String ideName) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        List<RecentSessionResponse> response =
                statsService.recent(currentUser.id(), limit, new SessionFilter(deviceId, ideName));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    @Operation(
            summary = "Heatmap year options",
            description =
                    "Lists the calendar years that contain valid coding sessions, newest first."
                            + " Feed for the dashboard heatmap year dropdown; derived from session"
                            + " data (start_time < end_time), not the lazily bootstrapped"
                            + " materialized table.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Years retrieved",
                        content = @Content(schema = @Schema(implementation = Integer[].class))),
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
                                                        value = UNAUTHORIZED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.READ)
    @RateLimit(type = RateLimitType.API, limit = 60, windowSeconds = 60)
    @GetMapping("/heatmap-years")
    public ResponseEntity<RestApiResponse<List<Integer>>> heatmapYears() {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        List<Integer> years = statsService.heatmapYears(currentUser.id());
        return ResponseEntity.ok(RestApiResponse.ok(years));
    }

    @Operation(
            summary = "IDE filter options",
            description =
                    "Lists the distinct non-blank IDE names registered by the user's devices, sorted."
                            + " Feed for the dashboard's IDE filter dropdown; the 'Unknown IDE' fallback"
                            + " bucket is never listed because it is not a registered value.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "IDE names retrieved",
                        content = @Content(schema = @Schema(implementation = String[].class))),
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
                                                        value = UNAUTHORIZED_EXAMPLE)))
            })
    @RequiresApiKeyScope(ApiKeyScope.READ)
    @RateLimit(type = RateLimitType.API, limit = 60, windowSeconds = 60)
    @GetMapping("/ide-filters")
    public ResponseEntity<RestApiResponse<List<String>>> ideFilters() {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        List<String> ides = statsService.ideFilters(currentUser.id());
        return ResponseEntity.ok(RestApiResponse.ok(ides));
    }

    @Operation(
            summary = "Coding achievements",
            description =
                    "Returns every achievement badge with its unlock state and progress, unlocking"
                            + " any badge whose progress reached its target. Window-based badges"
                            + " (early bird / night owl / perfect month) use the requested timezone.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Achievements retrieved",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AchievementResponse[].class))),
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
    @GetMapping("/achievements")
    public ResponseEntity<RestApiResponse<List<AchievementResponse>>> achievements(
            @RequestParam(name = "timezoneOffset", defaultValue = "0") @Min(-720) @Max(720)
                    int timezoneOffset) {
        CurrentUser currentUser = currentUserProvider.getCurrentUserRequired();
        List<AchievementResponse> response =
                achievementService.getAchievements(currentUser.id(), zoneOffset(timezoneOffset));
        return ResponseEntity.ok(RestApiResponse.ok(response));
    }

    private static ZoneOffset zoneOffset(int timezoneOffsetMinutes) {
        return ZoneOffset.ofTotalSeconds(timezoneOffsetMinutes * 60);
    }
}
