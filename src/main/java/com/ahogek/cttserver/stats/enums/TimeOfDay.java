package com.ahogek.cttserver.stats.enums;

/**
 * Broad time-of-day buckets for coding activity distribution, matching the plugin's weekly
 * statistics semantics: Night 00:00-05:59, Morning 06:00-11:59, Daytime 12:00-17:59, Evening
 * 18:00-23:59.
 *
 * <p>Sessions spanning multiple buckets are sliced at bucket boundaries so each fragment is
 * attributed to the bucket that contains it, and overlapping sessions are merged into unions before
 * slicing (see {@code StatsCalculator#timeOfDayDistribution}).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-30
 */
public enum TimeOfDay {
    MORNING,
    DAYTIME,
    EVENING,
    NIGHT;

    /**
     * Returns the bucket for a given hour of day.
     *
     * @param hour the hour of day (0-23)
     * @return the matching time-of-day bucket
     */
    public static TimeOfDay fromHour(int hour) {
        if (hour >= 6 && hour < 12) {
            return MORNING;
        }
        if (hour >= 12 && hour < 18) {
            return DAYTIME;
        }
        if (hour >= 18 && hour < 24) {
            return EVENING;
        }
        return NIGHT;
    }

    /**
     * Returns the hour that closes the bucket containing {@code hour} (24 for Evening, which ends
     * at midnight). Slicing walks between these boundaries.
     *
     * @param hour the hour of day (0-23)
     * @return the first hour outside the bucket containing {@code hour}
     */
    public static int boundaryAfter(int hour) {
        if (hour >= 0 && hour < 6) {
            return 6;
        }
        if (hour >= 6 && hour < 12) {
            return 12;
        }
        if (hour >= 12 && hour < 18) {
            return 18;
        }
        return 24;
    }
}
