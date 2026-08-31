package com.ahogek.cttserver.stats.achievement.enums;

/**
 * Achievement badge definitions.
 *
 * <p>Each badge carries its display metadata and the numeric threshold it unlocks at; progress is
 * derived from the user's coding sessions by {@link AchievementType}. The enum name is the stable
 * {@code achievement_code} stored in {@code user_achievements} — renaming a constant would orphan
 * existing unlock records, so new badges are added, never renames.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
public enum Achievement {
    STREAK_3(AchievementType.STREAK, 3, "days", "3-Day Streak", "Code on 3 consecutive days"),
    STREAK_7(AchievementType.STREAK, 7, "days", "7-Day Streak", "Code on 7 consecutive days"),
    STREAK_30(AchievementType.STREAK, 30, "days", "30-Day Streak", "Code on 30 consecutive days"),
    TOTAL_10_HOURS(
            AchievementType.TOTAL_SECONDS,
            36_000,
            "seconds",
            "10 Hours Total",
            "Accumulate 10 hours of coding"),
    TOTAL_100_HOURS(
            AchievementType.TOTAL_SECONDS,
            360_000,
            "seconds",
            "100 Hours Total",
            "Accumulate 100 hours of coding"),
    TOTAL_500_HOURS(
            AchievementType.TOTAL_SECONDS,
            1_800_000,
            "seconds",
            "500 Hours Total",
            "Accumulate 500 hours of coding"),
    LANGUAGES_3(
            AchievementType.LANGUAGE_COUNT,
            3,
            "languages",
            "Polyglot",
            "Code in 3 different languages"),
    LANGUAGES_5(
            AchievementType.LANGUAGE_COUNT,
            5,
            "languages",
            "Versatile Developer",
            "Code in 5 different languages"),
    LANGUAGES_10(
            AchievementType.LANGUAGE_COUNT,
            10,
            "languages",
            "Language Master",
            "Code in 10 different languages"),
    EARLY_BIRD_10(
            AchievementType.EARLY_BIRD_DAYS,
            10,
            "days",
            "Early Bird",
            "Code in the morning window (06:00-09:00) on 10 days"),
    EARLY_BIRD_30(
            AchievementType.EARLY_BIRD_DAYS,
            30,
            "days",
            "Morning Person",
            "Code in the morning window (06:00-09:00) on 30 days"),
    NIGHT_OWL_10(
            AchievementType.NIGHT_OWL_DAYS,
            10,
            "days",
            "Night Owl",
            "Code in the night window (22:00-05:00) on 10 days"),
    NIGHT_OWL_30(
            AchievementType.NIGHT_OWL_DAYS,
            30,
            "days",
            "After Midnight",
            "Code in the night window (22:00-05:00) on 30 days"),
    DAILY_BURST(
            AchievementType.MAX_DAILY_SECONDS,
            28_800,
            "seconds",
            "Sprint",
            "Code more than 8 hours in a single day"),
    PERFECT_MONTH(
            AchievementType.PERFECT_MONTH,
            1,
            "month",
            "Perfect Month",
            "Code on every day of a calendar month");

    private final AchievementType type;
    private final long target;
    private final String unit;
    private final String displayName;
    private final String description;

    Achievement(
            AchievementType type,
            long target,
            String unit,
            String displayName,
            String description) {
        this.type = type;
        this.target = target;
        this.unit = unit;
        this.displayName = displayName;
        this.description = description;
    }

    public AchievementType type() {
        return type;
    }

    public long target() {
        return target;
    }

    public String unit() {
        return unit;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
