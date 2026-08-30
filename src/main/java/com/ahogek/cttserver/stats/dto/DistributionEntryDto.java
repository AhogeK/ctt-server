package com.ahogek.cttserver.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One bucket of a distribution.
 *
 * @param name the bucket key (language, project, time-of-day or weekday)
 * @param seconds raw accumulated coding seconds
 */
@Schema(description = "One distribution bucket")
public record DistributionEntryDto(
        @Schema(description = "Bucket key", example = "Java") String name,
        @Schema(description = "Raw accumulated coding seconds", example = "360000") long seconds) {}
