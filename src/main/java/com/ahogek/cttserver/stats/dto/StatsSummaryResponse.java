package com.ahogek.cttserver.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Period summary in seconds.
 *
 * @param today seconds coded today (aggregation timezone)
 * @param dailyAverage lifetime total divided by days since the first session day, inclusive
 * @param thisWeek seconds coded this ISO week (Monday start)
 * @param thisMonth seconds coded this calendar month
 * @param thisYear seconds coded this calendar year
 * @param total lifetime seconds (overlaps merged)
 */
@Schema(description = "Coding activity summary in seconds")
public record StatsSummaryResponse(
        @Schema(description = "Seconds coded today", example = "3600") long today,
        @Schema(
                        description = "Lifetime total divided by days since first session day",
                        example = "1200")
                long dailyAverage,
        @Schema(description = "Seconds coded this ISO week (Monday start)", example = "25200")
                long thisWeek,
        @Schema(description = "Seconds coded this calendar month", example = "108000")
                long thisMonth,
        @Schema(description = "Seconds coded this calendar year", example = "1296000")
                long thisYear,
        @Schema(description = "Lifetime seconds (overlapping sessions merged)", example = "5000000")
                long total) {}
