package com.ahogek.cttserver.leaderboard.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ranking dimension for the global leaderboard.
 *
 * <p>Each dimension is backed by dedicated Redis ZSet keys: the S2 lifetime keys ({@code
 * leaderboard:total}, {@code leaderboard:streak}) for {@link LeaderboardPeriod#ALL}, plus
 * period-bucketed keys ({@code leaderboard:total:week:...}, ...) for period rankings. Scores are
 * derived from the user's coding sessions: merged lifetime/period duration, longest consecutive
 * coding-day streak, merged night-owl window duration, merged early-bird window duration, or the
 * week-over-week net growth.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Schema(description = "Leaderboard ranking dimension")
public enum LeaderboardDimension {
    /** Lifetime or period coding duration in seconds (merged, overlap collapsed). */
    TOTAL(null, null),
    /** Longest consecutive coding-day streak (UTC). */
    STREAK(null, null),
    /** Merged coding duration inside the night-owl window 22:00-05:00 (UTC). */
    NIGHT_OWL(22, 5),
    /** Merged coding duration inside the early-bird window 06:00-09:00 (UTC). */
    EARLY_BIRD(6, 9),
    /** Week-over-week net growth in seconds (this week minus last week). */
    GROWTH(null, null);

    private final Integer windowStartHour;
    private final Integer windowEndHour;

    LeaderboardDimension(Integer windowStartHour, Integer windowEndHour) {
        this.windowStartHour = windowStartHour;
        this.windowEndHour = windowEndHour;
    }

    /**
     * Returns the daily window's start hour for a time-window dimension.
     *
     * @return the window start hour (0-23)
     * @throws IllegalStateException when the dimension has no daily window
     */
    public int windowStartHour() {
        if (windowStartHour == null) {
            throw new IllegalStateException(this + " has no daily window");
        }
        return windowStartHour;
    }

    /**
     * Returns the hour just after the daily window ends (may be before the start hour to cross
     * midnight).
     *
     * @return the window end hour (0-23)
     * @throws IllegalStateException when the dimension has no daily window
     */
    public int windowEndHour() {
        if (windowEndHour == null) {
            throw new IllegalStateException(this + " has no daily window");
        }
        return windowEndHour;
    }

    /**
     * Returns whether the dimension can be ranked within the given period.
     *
     * @param period the requested time window
     * @return {@code true} for a legal dimension/period combination
     */
    public boolean supports(LeaderboardPeriod period) {
        return switch (this) {
            case TOTAL -> true;
            case STREAK -> period == LeaderboardPeriod.ALL;
            case NIGHT_OWL, EARLY_BIRD -> period == LeaderboardPeriod.ALL;
            case GROWTH -> period == LeaderboardPeriod.WEEK;
        };
    }
}
