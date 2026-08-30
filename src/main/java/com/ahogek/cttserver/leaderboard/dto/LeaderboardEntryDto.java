package com.ahogek.cttserver.leaderboard.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One ranked user in the leaderboard.
 *
 * @param userId the ranked user id
 * @param displayName the user's display name, or {@code null} when the account no longer exists
 * @param score the user's score for the requested dimension (seconds or streak days)
 * @param rank 1-based position in the leaderboard
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Schema(description = "A ranked user entry")
public record LeaderboardEntryDto(
        @Schema(description = "Ranked user id", example = "3f2e1d0c-…") UUID userId,
        @Schema(description = "User display name", example = "AhogeK") String displayName,
        @Schema(description = "Score for the requested dimension", example = "237776") long score,
        @Schema(description = "1-based position in the leaderboard", example = "1") long rank) {}
