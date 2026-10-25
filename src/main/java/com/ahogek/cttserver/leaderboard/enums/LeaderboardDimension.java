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
    ACTIVE_DAYS(null, null),
    /**
     * Ranking inside a single language: one board per language, selected by the caller.
     *
     * <p>This is the only partitioned dimension — there is no single "language" board, so the
     * request must name one. Its score is the merged duration in that language, so two overlapping
     * sessions in the same language count once.
     *
     * <p>Boards exist only for languages the vocabulary recognizes and does not classify as {@code
     * Other}. That keeps the key space bounded by the vocabulary rather than by whatever strings
     * clients submit: an unrecognized value is still stored and reported so it can be classified,
     * but it does not create a board until it is.
     */
    LANGUAGE(null, null);

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
        // Every dimension ranks every period. A windowed streak is the longest run of consecutive
        // coding days fully inside the window (see LeaderboardService#longestRunInside); it is not
        // the streak as of the window's close, which would be undefined while the period is still
        // running and would change retroactively as the window advances.
        return switch (this) {
            case TOTAL, STREAK, NIGHT_OWL, EARLY_BIRD, ACTIVE_DAYS, LANGUAGE -> true;
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

    /**
     * Returns whether only positive scores earn a place on this dimension's boards.
     *
     * <p>Every dimension except {@link #GROWTH} measures activity, so a score of zero means the
     * user did none of it inside the window. Ranking them anyway is not a neutral default: it puts
     * accounts that never coded on the board at the bottom, ties them all at the same rank, and
     * counts them in {@code totalParticipants}, so "rank 3 of 9" describes eight accounts that have
     * nothing to show. A user with no activity is absent from the board rather than ranked at zero,
     * which also makes their {@code currentUserRank} {@code null} — the same answer the API already
     * gives for anyone outside a board.
     *
     * <p>{@link #GROWTH} is the exception: its score is a delta, and zero (or a negative value) is
     * a measurement — "no change" or "less than the period before" — not the absence of one.
     *
     * @return {@code true} for every dimension but {@link #GROWTH}
     */
    public boolean ranksOnlyPositiveScores() {
        return this != GROWTH;
    }

    /**
     * Returns whether this dimension needs a language to be named.
     *
     * <p>Checked by the controller rather than assumed, so a caller that forgets the parameter
     * fails with a validation error instead of silently reading a board nobody writes.
     *
     * @return {@code true} for {@link #LANGUAGE}
     */
    public boolean requiresLanguage() {
        return this == LANGUAGE;
    }
}
