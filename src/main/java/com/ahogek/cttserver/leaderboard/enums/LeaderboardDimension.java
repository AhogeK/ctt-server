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
    /** Net growth against the immediately preceding period, in seconds (may be negative). */
    GROWTH(null, null),
    /** Number of distinct days carrying coding time — consistency rather than volume. */
    ACTIVE_DAYS(null, null);

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
     * <p>{@link #ALL} is legal for every dimension. The period windows differ in what a ranked
     * number means for them:
     *
     * <ul>
     *   <li>{@link #TOTAL}, {@link #NIGHT_OWL}, {@link #EARLY_BIRD} and {@link #ACTIVE_DAYS} are
     *       measurements inside the window, so every period is meaningful — "who coded most this
     *       week" and "who was the night owl this month" are the same question over different
     *       spans.
     *   <li>{@link #STREAK} is a run length, and the periods are all shorter than the runs it
     *       rewards, so it is only ranked over all time.
     *   <li>{@link #GROWTH} compares a period against the one before it, which an unbounded history
     *       cannot do; {@link LeaderboardPeriod#WEEK} remains the default for callers that omit it.
     * </ul>
     *
     * @param period the requested time window
     * @return {@code true} for a legal dimension/period combination
     */
    public boolean supports(LeaderboardPeriod period) {
        return switch (this) {
            case TOTAL, NIGHT_OWL, EARLY_BIRD, ACTIVE_DAYS -> true;
            case STREAK -> period == LeaderboardPeriod.ALL;
            case GROWTH -> period != LeaderboardPeriod.ALL;
        };
    }

    /**
     * Returns the period to use when a caller omits one.
     *
     * <p>Lives here rather than in the controller so the legal set and the default are decided in
     * one place: a dimension whose default were absent from {@link #supports} would reject its own
     * default. Every dimension defaults to {@link LeaderboardPeriod#ALL} except {@link #GROWTH},
     * which cannot rank an unbounded history and therefore falls back to the shortest window it
     * supports.
     *
     * @return the default period, always legal for this dimension
     */
    public LeaderboardPeriod defaultPeriod() {
        return this == GROWTH ? LeaderboardPeriod.WEEK : LeaderboardPeriod.ALL;
    }
}
