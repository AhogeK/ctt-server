package com.ahogek.cttserver.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-metric change against the previous complete period.
 *
 * <p>Every key is present on every response: a metric without a baseline carries a {@code null}
 * {@code changePercent} instead of being dropped, so a client reads the object once rather than
 * also testing whether the object exists. {@code total} is absent by design — a lifetime figure has
 * no previous period.
 *
 * @param today today against yesterday
 * @param dailyAverage lifetime daily average against its value at the end of yesterday
 * @param thisWeek this ISO week against last week
 * @param thisMonth this calendar month against last month
 * @param thisYear this calendar year against last year
 */
@Schema(description = "Per-metric change against the previous complete period")
public record StatsComparisons(
        @Schema(
                        description = "Today against yesterday",
                        example = "{\"previousSeconds\": 3600, \"changePercent\": 12.5}")
                StatsComparison today,
        @Schema(
                        description =
                                "Lifetime daily average against its value at the end of yesterday;"
                                        + " the null is the first coding day, the only baseline-less"
                                        + " case",
                        example = "{\"previousSeconds\": 0, \"changePercent\": null}")
                StatsComparison dailyAverage,
        @Schema(
                        description = "This ISO week against last week",
                        example = "{\"previousSeconds\": 25200, \"changePercent\": -85.71}")
                StatsComparison thisWeek,
        @Schema(
                        description = "This calendar month against last month",
                        example = "{\"previousSeconds\": 108000, \"changePercent\": -33.33}")
                StatsComparison thisMonth,
        @Schema(
                        description = "This calendar year against last year",
                        example = "{\"previousSeconds\": 1296000, \"changePercent\": 8.33}")
                StatsComparison thisYear) {}
