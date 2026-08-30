package com.ahogek.cttserver.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-hour usage averaged across active days.
 *
 * @param hour the hour of day (0-23)
 * @param averageSeconds average seconds coded in that hour across active days
 */
@Schema(description = "Per-hour average coding usage")
public record HourlyStatPoint(
        @Schema(description = "Hour of day (0-23)", example = "9") int hour,
        @Schema(description = "Average seconds in that hour across active days", example = "1800")
                long averageSeconds) {}
