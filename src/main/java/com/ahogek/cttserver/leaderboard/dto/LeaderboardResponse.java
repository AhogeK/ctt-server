package com.ahogek.cttserver.leaderboard.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Leaderboard page for one dimension.
 *
 * @param entries ranked users in score-descending order for the requested page
 * @param currentUserRank the calling user's 1-based rank, or {@code null} when the user has no
 *     score yet (never pushed)
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Schema(description = "Leaderboard response")
public record LeaderboardResponse(
        @Schema(description = "Ranked user entries") List<LeaderboardEntryDto> entries,
        @Schema(description = "Calling user's 1-based rank, or null when not ranked", example = "3")
                Long currentUserRank) {}
