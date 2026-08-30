package com.ahogek.cttserver.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Consecutive coding day streaks.
 *
 * @param current current streak ending today or yesterday
 * @param max longest streak ever recorded
 */
@Schema(description = "Consecutive coding day streaks")
public record StreakStatsResponse(
        @Schema(description = "Current streak ending today or yesterday", example = "7")
                int current,
        @Schema(description = "Longest streak ever recorded", example = "30") int max) {}
