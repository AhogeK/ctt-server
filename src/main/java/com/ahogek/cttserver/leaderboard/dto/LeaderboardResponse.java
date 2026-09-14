package com.ahogek.cttserver.leaderboard.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Leaderboard page for one dimension.
 *
 * <p>{@code currentUserRank} and the {@code rank} on each entry are computed by the same rule —
 * standard competition ranking, where tied scores share a position and the next distinct score
 * resumes after the gap. They are therefore always consistent with each other, including on a page
 * the caller does not appear in.
 *
 * <p>{@code totalParticipants} is the size of the ranking, which a client needs to place a rank in
 * context ("12th of 1,240") and to page without guessing whether another page exists.
 *
 * @param entries ranked users in score-descending order for the requested page
 * @param currentUserRank the calling user's 1-based rank, or {@code null} when the user has no
 *     score for this dimension and period
 * @param totalParticipants how many users are ranked for this dimension and period
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Schema(description = "Leaderboard response")
public record LeaderboardResponse(
        @Schema(description = "Ranked user entries") List<LeaderboardEntryDto> entries,
        @Schema(description = "Calling user's 1-based rank, or null when not ranked", example = "3")
                Long currentUserRank,
        @Schema(
                        description = "Number of users ranked for this dimension and period",
                        example = "1240")
                long totalParticipants) {}
