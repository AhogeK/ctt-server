package com.ahogek.cttserver.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Average coding usage for one weekday-hour cell of the weekly heatmap.
 *
 * @param dayOfWeek ISO weekday (1=Monday..7=Sunday)
 * @param hour hour of day (0-23)
 * @param averageSeconds average seconds coded in that weekday-hour across the days that weekday
 *     appears in the window
 */
@Schema(description = "One weekday-hour cell of the weekly coding heatmap")
public record WeekHourStatPoint(
        @Schema(description = "ISO weekday, 1=Monday..7=Sunday", example = "2") int dayOfWeek,
        @Schema(description = "Hour of day (0-23)", example = "10") int hour,
        @Schema(description = "Average seconds in that weekday-hour", example = "1800")
                long averageSeconds) {}
