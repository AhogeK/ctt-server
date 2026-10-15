package com.ahogek.cttserver.stats.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-day hourly coding seconds for a window.
 *
 * <p>The shape the "Average hourly coding duration" panel needs to draw each day behind the average
 * curve: summing every day's {@code hourlySeconds} reproduces, hour by hour, the numerators that
 * {@link HourlyDistributionResponse} divides by {@code activeDays}, because both are derived from
 * the same accumulation.
 *
 * @param days active days in ascending date order, at most the 31 most recent
 * @param activeDays days with any coding in the window; equal to the denominator of a matching
 *     {@link HourlyDistributionResponse}, and not reduced by the day cap
 */
@Schema(description = "Per-day hourly coding seconds across the active days of a window")
public record HourlyDaysResponse(
        @Schema(
                        description = "Active days in ascending order (at most the 31 most recent)",
                        example = "[{\"date\": \"2026-10-12\", \"hourlySeconds\": [0, 0]}]")
                List<HourlyDayPoint> days,
        @Schema(description = "Days with any coding activity in the window", example = "12")
                int activeDays) {}
