package com.ahogek.cttserver.stats.enums;

/**
 * Broad time-of-day buckets for coding activity distribution.
 *
 * <p>Bucketing is based on the session's start hour in the aggregation timezone: Morning
 * 05:00-11:59, Daytime 12:00-16:59, Evening 17:00-21:59, Night 22:00-04:59.
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
        if (hour >= 5 && hour < 12) {
            return MORNING;
        }
        if (hour >= 12 && hour < 17) {
            return DAYTIME;
        }
        if (hour >= 17 && hour < 22) {
            return EVENING;
        }
        return NIGHT;
    }
}
