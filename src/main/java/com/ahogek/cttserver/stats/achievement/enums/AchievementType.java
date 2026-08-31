package com.ahogek.cttserver.stats.achievement.enums;

/**
 * Achievement category that determines how progress is computed from coding sessions.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
public enum AchievementType {
    /** Longest consecutive coding-day streak (days). */
    STREAK,
    /** Lifetime merged coding duration (seconds). */
    TOTAL_SECONDS,
    /** Number of distinct programming languages used. */
    LANGUAGE_COUNT,
    /** Distinct days with coding inside the 06:00-09:00 window. */
    EARLY_BIRD_DAYS,
    /** Distinct days with coding inside the 22:00-05:00 window. */
    NIGHT_OWL_DAYS,
    /** Longest single-day merged coding duration (seconds). */
    MAX_DAILY_SECONDS,
    /** Whether any calendar month was coded on every single day. */
    PERFECT_MONTH
}
