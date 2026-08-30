package com.ahogek.cttserver.stats.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One day of the daily heatmap.
 *
 * @param date the calendar day
 * @param seconds merged coding seconds on that day
 */
@Schema(description = "One day of the coding heatmap")
public record DailyStatPoint(
        @Schema(description = "Calendar day", example = "2026-08-01") LocalDate date,
        @Schema(description = "Merged coding seconds on that day", example = "7200")
                long seconds) {}
