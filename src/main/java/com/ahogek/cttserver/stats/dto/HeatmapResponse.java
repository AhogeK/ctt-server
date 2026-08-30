package com.ahogek.cttserver.stats.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Daily heatmap response.
 *
 * @param points daily points in date order, one per day of the requested range
 */
@Schema(description = "Daily coding heatmap over a date range")
public record HeatmapResponse(
        @Schema(description = "Daily points in date order") List<DailyStatPoint> points) {}
