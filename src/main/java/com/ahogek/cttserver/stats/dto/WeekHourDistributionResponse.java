package com.ahogek.cttserver.stats.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Weekly coding heatmap response.
 *
 * @param points exercised weekday-hour cells, weekday then hour order; unexercised cells are
 *     omitted and rendered as zero by the client
 * @param weekdayCounts per-weekday day counts in the aggregation window (the averaging
 *     denominators), keyed by ISO weekday 1=Monday..7=Sunday
 */
@Schema(description = "Weekly coding activity by hour (7x24 heatmap)")
public record WeekHourDistributionResponse(
        @Schema(
                        description = "Exercised weekday-hour cells, weekday then hour order",
                        example = "[{\"dayOfWeek\": 2, \"hour\": 10, \"averageSeconds\": 1800}]")
                List<WeekHourStatPoint> points,
        @Schema(
                        description =
                                "Days each weekday appears in the window, keyed 1=Monday..7=Sunday",
                        example = "{\"1\": 12, \"2\": 13}")
                Map<Integer, Integer> weekdayCounts) {}
