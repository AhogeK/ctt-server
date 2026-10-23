package com.ahogek.cttserver.leaderboard.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Time window for a leaderboard dimension.
 *
 * <p>{@link #ALL} is the lifetime global ranking (the S2 keys {@code leaderboard:total} / {@code
 * leaderboard:streak}), while every other period ranks users within the current calendar window:
 * weeks start on ISO Monday, months on the 1st, quarters on the first day of Jan/Apr/Jul/Oct, half
 * years on the first day of Jan/Jul and years on Jan 1 (all UTC). Period keys are bucketed by their
 * period start and expire after the period closes.
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
    /** Current calendar quarter (Jan–Mar, Apr–Jun, Jul–Sep, Oct–Dec, UTC). */
    QUARTER,
    /** Current calendar half year (Jan–Jun, Jul–Dec, UTC). */
    HALF_YEAR,
    /** Current calendar year (UTC). */
    YEAR;

    /**
     * Returns the first date of the period containing the reference date.
     *
     * <p>Weeks start on ISO Monday, matching the period bucket key, so the window a score is
     * measured over and the key it is stored under can never disagree.
     *
     * @param today the reference date
     * @return the inclusive period start, or {@code null} for {@link #ALL}, which is unbounded
     */
    public LocalDate start(LocalDate today) {
        return switch (this) {
            case ALL -> null;
            case WEEK -> today.with(DayOfWeek.MONDAY);
            case MONTH -> today.withDayOfMonth(1);
            case QUARTER ->
                    today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1);
            case HALF_YEAR -> today.withDayOfMonth(1).withMonth(today.getMonthValue() <= 6 ? 1 : 7);
            case YEAR -> today.withDayOfYear(1);
        };
    }

    /**
     * Returns the first date of the period after the one containing the reference date.
     *
     * <p>Exclusive, so a window is always the half-open range {@code [start, end)}. That is the
     * shape every duration aggregation in this service expects, and it keeps a session sitting
     * exactly on the boundary in one period rather than two.
     *
     * @param today the reference date
     * @return the exclusive period end, or {@code null} for {@link #ALL}
     */
    public LocalDate endExclusive(LocalDate today) {
        LocalDate start = start(today);
        if (start == null) {
            return null;
        }
        return switch (this) {
            case ALL -> null;
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
            case QUARTER -> start.plusMonths(3);
            case HALF_YEAR -> start.plusMonths(6);
            case YEAR -> start.plusYears(1);
        };
    }

    /**
     * Returns the period key fragment identifying the current period, or an empty string for {@link
     * #ALL}.
     *
     * <p>Empty rather than {@code "all"} so the lifetime key stays exactly what earlier releases
     * wrote ({@code leaderboard:total}); changing it would orphan every existing score.
     *
     * @param today the reference date
     * @return the key fragment including its leading separator, or an empty string
     */
    public String keySuffix(LocalDate today) {
        LocalDate start = start(today);
        if (start == null) {
            return "";
        }
        return switch (this) {
            case ALL -> "";
            case WEEK -> ":week:" + start;
            case MONTH -> ":month:" + start;
            case QUARTER -> ":quarter:" + start;
            case HALF_YEAR -> ":half-year:" + start;
            case YEAR -> ":year:" + start;
        };
    }
}
