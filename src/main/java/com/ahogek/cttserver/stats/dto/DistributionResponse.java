package com.ahogek.cttserver.stats.dto;

import com.ahogek.cttserver.stats.enums.DistributionType;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A distribution response.
 *
 * @param type the requested dimension
 * @param entries buckets ordered by duration descending
 */
@Schema(description = "Coding duration distribution by dimension")
public record DistributionResponse(
        @Schema(description = "Distribution dimension") DistributionType type,
        @Schema(description = "Buckets ordered by duration descending")
                List<DistributionEntryDto> entries) {}
