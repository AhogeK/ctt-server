package com.ahogek.cttserver.stats.achievement.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;

/**
 * The measurement window an achievement is scoped to.
 *
 * <p>Lifetime badges accumulate forever and are therefore exhaustible — once every rung is taken
 * there is nothing left to reach. Windowed badges reset with each period, so the same goal is
 * available again next day, week, month or year and the ladder never runs out.
 *
 * <p>Periods follow the <em>caller's</em> local calendar, matching how every statistics endpoint
 * resolves day, week, month and year from the request's timezone: a week boundary derived from UTC
 * would reset a UTC+8 user's progress at 08:00 on Monday.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-13
 */
public enum AchievementWindow {
    /** No window: progress accumulates over the whole history and never resets. */
    LIFETIME,
    /** The local calendar day containing the reference date. */
    DAY,
    /** The ISO week (Monday through Sunday) containing the reference date. */
    WEEK,
    /** The local calendar month containing the reference date. */
    MONTH,
    /** The local calendar year containing the reference date. */
    YEAR;

    /** The window's period key for a lifetime badge, which has no period to distinguish. */
    public static final String LIFETIME_PERIOD = "LIFETIME";

    /**
     * Returns the first date of this window's current period.
     *
     * @param today the caller's local date
     * @return the inclusive period start, or {@code null} for {@link #LIFETIME}
     */
    public LocalDate start(LocalDate today) {
        return switch (this) {
            case LIFETIME -> null;
            case DAY -> today;
            case WEEK -> today.with(DayOfWeek.MONDAY);
            case MONTH -> today.withDayOfMonth(1);
            case YEAR -> today.withDayOfYear(1);
        };
    }

    /**
     * Returns the last date of this window's current period.
     *
     * @param today the caller's local date
     * @return the inclusive period end, or {@code null} for {@link #LIFETIME}
     */
    public LocalDate end(LocalDate today) {
        return switch (this) {
            case LIFETIME -> null;
            case DAY -> today;
            case WEEK -> today.with(DayOfWeek.MONDAY).plusDays(6);
            case MONTH -> today.withDayOfMonth(today.lengthOfMonth());
            case YEAR -> today.withDayOfYear(today.lengthOfYear());
        };
    }

    /**
     * Returns the stable key identifying this window's current period.
     *
     * <p>Stored alongside each unlock so a badge is awarded at most once per period: next period
     * carries a different key and the badge becomes reachable again. The ISO week-based year is
     * used for weeks rather than the calendar year, so a week straddling New Year keeps one
     * identity.
     *
     * @param today the caller's local date
     * @return the period key ({@code LIFETIME}, {@code 2026-09-13}, {@code 2026-W37}, {@code
     *     2026-09} or {@code 2026})
     */
    public String periodKey(LocalDate today) {
        return switch (this) {
            case LIFETIME -> LIFETIME_PERIOD;
            case DAY -> today.toString();
            case WEEK ->
                    String.format(
                            "%d-W%02d",
                            today.get(WeekFields.ISO.weekBasedYear()),
                            today.get(WeekFields.ISO.weekOfWeekBasedYear()));
            case MONTH -> String.format("%d-%02d", today.getYear(), today.getMonthValue());
            case YEAR -> String.valueOf(today.getYear());
        };
    }

    /**
     * Returns a date inside the period immediately before the one containing the given date.
     *
     * <p>Counting consecutive periods walks backwards with this rather than subtracting a guessed
     * length: weeks are stepped with {@code minusWeeks} so the ISO week-based year is handled by
     * the calendar (a week straddling New Year is still one step), and months use {@code
     * minusMonths} rather than 30 days so February is not skipped.
     *
     * @param reference a date inside a period
     * @return a date inside the preceding period, or {@code null} for {@link #LIFETIME}
     */
    public LocalDate previousPeriod(LocalDate reference) {
        return switch (this) {
            case LIFETIME -> null;
            case DAY -> reference.minusDays(1);
            case WEEK -> reference.minusWeeks(1);
            case MONTH -> reference.minusMonths(1);
            case YEAR -> reference.minusYears(1);
        };
    }
}
