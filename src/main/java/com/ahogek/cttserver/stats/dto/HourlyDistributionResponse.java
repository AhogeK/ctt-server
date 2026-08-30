package com.ahogek.cttserver.stats.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Hourly distribution response.
 *
 * @param points one entry per hour (0-23)
 * @param activeDays number of days with any coding activity (the averaging denominator)
 */
@Schema(description = "Hourly coding distribution across active days")
public record HourlyDistributionResponse(
        @Schema(description = "Per-hour averages, hour order") List<HourlyStatPoint> points,
        @Schema(description = "Days with any coding activity", example = "42") int activeDays) {}
