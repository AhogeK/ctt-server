package com.ahogek.cttserver.leaderboard.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Time window for a leaderboard dimension.
 *
 * <p>{@link #ALL} is the lifetime global ranking (the S2 keys {@code leaderboard:total} / {@code
 * leaderboard:streak}), while {@link #WEEK}, {@link #MONTH} and {@link #YEAR} rank users within the
 * current calendar period. Period keys are bucketed by their period start (ISO Monday for weeks)
 * and expire after the period closes.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Schema(description = "Leaderboard time window")
public enum LeaderboardPeriod {
    /** Lifetime (global) ranking, never expires. */
    ALL,
    /** Current calendar week (ISO 8601, starts Monday, UTC). */
    WEEK,
    /** Current calendar month (UTC). */
    MONTH,
    /** Current calendar year (UTC). */
    YEAR
}
