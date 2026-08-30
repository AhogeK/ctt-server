package com.ahogek.cttserver.leaderboard.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ranking dimension for the global leaderboard.
 *
 * <p>Each dimension is backed by a dedicated Redis ZSet key ({@code leaderboard:total}, {@code
 * leaderboard:streak}) whose score is the user's coding-seconds or longest consecutive coding-day
 * streak.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Schema(description = "Leaderboard ranking dimension")
public enum LeaderboardDimension {
    /** Lifetime coding duration in seconds. */
    TOTAL,
    /** Longest consecutive coding-day streak. */
    STREAK
}
