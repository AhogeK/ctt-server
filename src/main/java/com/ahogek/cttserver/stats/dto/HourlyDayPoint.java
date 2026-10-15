package com.ahogek.cttserver.stats.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One active day's coding seconds, hour by hour.
 *
 * @param date the local day
 * @param hourlySeconds seconds coded in each hour of that day, index 0-23; always 24 entries
 */
@Schema(description = "One active day of the per-day hourly distribution")
public record HourlyDayPoint(
        @Schema(description = "Local day", example = "2026-10-12") LocalDate date,
        @Schema(
                        description = "Seconds coded in each hour of that day (24 entries, 0-23)",
                        example =
                                "[0, 0, 0, 0, 0, 0, 0, 0, 0, 120, 3600, 0, 0, 0, 0, 0, 0, 0, 0,"
                                        + " 0, 0, 0, 0, 0]")
                List<Long> hourlySeconds) {}
