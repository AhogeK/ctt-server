package com.ahogek.cttserver.stats.achievement.enums;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Achievement badge definitions.
 *
 * <p>Each badge carries its display metadata and the numeric threshold it unlocks at; progress is
 * derived from the user's coding sessions by {@link AchievementType}. The enum name is the stable
 * {@code achievement_code} stored in {@code user_achievements} — renaming a constant would orphan
 * existing unlock records, so new badges are added, never renames.
 *
 * <p>Constants are grouped by {@link AchievementType} and listed in ascending {@code target} order;
 * that order is the ladder the client renders. The tier ordinal is derived from the ladder rather
 * than declared, so inserting a rung never renumbers its neighbours by hand.
 *
 * <p>Thresholds rise so the time between two unlocks grows smoothly: the first rungs land inside
 * the first weeks of use, the last ones are long-haul goals. The step factor is calibrated per
 * family rather than to one shared number, because the domains differ: the wide ladders (streak,
 * total time) climb by roughly x2 per rung, while language count uses smaller steps and the
 * percent-based month ladder necessarily compresses as it approaches its ceiling. Two families are
 * additionally bounded by their domain — a day has 24 hours and a month is at most 100 percent
 * covered — so their rungs cannot keep doubling, and every ladder must keep the rungs that were
 * shipped before.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
public enum Achievement {
    STREAK_3(AchievementType.STREAK, 3, "days", "3-Day Streak", "Code on 3 consecutive days"),
    STREAK_7(AchievementType.STREAK, 7, "days", "7-Day Streak", "Code on 7 consecutive days"),
    STREAK_14(AchievementType.STREAK, 14, "days", "14-Day Streak", "Code on 14 consecutive days"),
    STREAK_30(AchievementType.STREAK, 30, "days", "30-Day Streak", "Code on 30 consecutive days"),
    STREAK_60(AchievementType.STREAK, 60, "days", "60-Day Streak", "Code on 60 consecutive days"),
    STREAK_100(
            AchievementType.STREAK, 100, "days", "100-Day Streak", "Code on 100 consecutive days"),
    STREAK_180(
            AchievementType.STREAK, 180, "days", "180-Day Streak", "Code on 180 consecutive days"),
    STREAK_365(
            AchievementType.STREAK, 365, "days", "365-Day Streak", "Code on 365 consecutive days"),

    TOTAL_10_HOURS(
            AchievementType.TOTAL_SECONDS,
            36_000,
            "seconds",
            "10 Hours Total",
            "Accumulate 10 hours of coding"),
    TOTAL_25_HOURS(
            AchievementType.TOTAL_SECONDS,
            90_000,
            "seconds",
            "25 Hours Total",
            "Accumulate 25 hours of coding"),
    TOTAL_50_HOURS(
            AchievementType.TOTAL_SECONDS,
            180_000,
            "seconds",
            "50 Hours Total",
            "Accumulate 50 hours of coding"),
    TOTAL_100_HOURS(
            AchievementType.TOTAL_SECONDS,
            360_000,
            "seconds",
            "100 Hours Total",
            "Accumulate 100 hours of coding"),
    TOTAL_250_HOURS(
            AchievementType.TOTAL_SECONDS,
            900_000,
            "seconds",
            "250 Hours Total",
            "Accumulate 250 hours of coding"),
    TOTAL_500_HOURS(
            AchievementType.TOTAL_SECONDS,
            1_800_000,
            "seconds",
            "500 Hours Total",
            "Accumulate 500 hours of coding"),
    TOTAL_1000_HOURS(
            AchievementType.TOTAL_SECONDS,
            3_600_000,
            "seconds",
            "1000 Hours Total",
            "Accumulate 1000 hours of coding"),
    TOTAL_2500_HOURS(
            AchievementType.TOTAL_SECONDS,
            9_000_000,
            "seconds",
            "2500 Hours Total",
            "Accumulate 2500 hours of coding"),

    LANGUAGES_2(
            AchievementType.LANGUAGE_COUNT,
            2,
            "languages",
            "Bilingual",
            "Code in 2 different languages"),
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
    LANGUAGES_8(
            AchievementType.LANGUAGE_COUNT,
            8,
            "languages",
            "Multilingual",
            "Code in 8 different languages"),
    LANGUAGES_10(
            AchievementType.LANGUAGE_COUNT,
            10,
            "languages",
            "Language Master",
            "Code in 10 different languages"),
    LANGUAGES_15(
            AchievementType.LANGUAGE_COUNT,
            15,
            "languages",
            "Language Collector",
            "Code in 15 different languages"),
    LANGUAGES_25(
            AchievementType.LANGUAGE_COUNT,
            25,
            "languages",
            "Language Archivist",
            "Code in 25 different languages"),
    LANGUAGES_40(
            AchievementType.LANGUAGE_COUNT,
            40,
            "languages",
            "Language Virtuoso",
            "Code in 40 different languages"),
    LANGUAGES_60(
            AchievementType.LANGUAGE_COUNT,
            60,
            "languages",
            "Omniglot",
            "Code in 60 different languages"),

    EARLY_BIRD_5(
            AchievementType.EARLY_BIRD_DAYS,
            5,
            "days",
            "Early Riser",
            "Code in the morning window (06:00-09:00) on 5 days"),
    EARLY_BIRD_10(
            AchievementType.EARLY_BIRD_DAYS,
            10,
            "days",
            "Early Bird",
            "Code in the morning window (06:00-09:00) on 10 days"),
    EARLY_BIRD_20(
            AchievementType.EARLY_BIRD_DAYS,
            20,
            "days",
            "Morning Regular",
            "Code in the morning window (06:00-09:00) on 20 days"),
    EARLY_BIRD_30(
            AchievementType.EARLY_BIRD_DAYS,
            30,
            "days",
            "Morning Person",
            "Code in the morning window (06:00-09:00) on 30 days"),
    EARLY_BIRD_50(
            AchievementType.EARLY_BIRD_DAYS,
            50,
            "days",
            "Dawn Specialist",
            "Code in the morning window (06:00-09:00) on 50 days"),
    EARLY_BIRD_75(
            AchievementType.EARLY_BIRD_DAYS,
            75,
            "days",
            "Morning Veteran",
            "Code in the morning window (06:00-09:00) on 75 days"),
    EARLY_BIRD_150(
            AchievementType.EARLY_BIRD_DAYS,
            150,
            "days",
            "Sunrise Master",
            "Code in the morning window (06:00-09:00) on 150 days"),
    EARLY_BIRD_300(
            AchievementType.EARLY_BIRD_DAYS,
            300,
            "days",
            "Morning Legend",
            "Code in the morning window (06:00-09:00) on 300 days"),

    NIGHT_OWL_5(
            AchievementType.NIGHT_OWL_DAYS,
            5,
            "days",
            "Night Riser",
            "Code in the night window (22:00-05:00) on 5 days"),
    NIGHT_OWL_10(
            AchievementType.NIGHT_OWL_DAYS,
            10,
            "days",
            "Night Owl",
            "Code in the night window (22:00-05:00) on 10 days"),
    NIGHT_OWL_20(
            AchievementType.NIGHT_OWL_DAYS,
            20,
            "days",
            "Late Regular",
            "Code in the night window (22:00-05:00) on 20 days"),
    NIGHT_OWL_30(
            AchievementType.NIGHT_OWL_DAYS,
            30,
            "days",
            "After Midnight",
            "Code in the night window (22:00-05:00) on 30 days"),
    NIGHT_OWL_50(
            AchievementType.NIGHT_OWL_DAYS,
            50,
            "days",
            "Night Specialist",
            "Code in the night window (22:00-05:00) on 50 days"),
    NIGHT_OWL_75(
            AchievementType.NIGHT_OWL_DAYS,
            75,
            "days",
            "Night Veteran",
            "Code in the night window (22:00-05:00) on 75 days"),
    NIGHT_OWL_150(
            AchievementType.NIGHT_OWL_DAYS,
            150,
            "days",
            "Midnight Master",
            "Code in the night window (22:00-05:00) on 150 days"),
    NIGHT_OWL_300(
            AchievementType.NIGHT_OWL_DAYS,
            300,
            "days",
            "Night Legend",
            "Code in the night window (22:00-05:00) on 300 days"),

    DAILY_BURST_4(
            AchievementType.MAX_DAILY_SECONDS,
            14_400,
            "seconds",
            "Deep Work",
            "Code 4 hours in a single day"),
    DAILY_BURST_6(
            AchievementType.MAX_DAILY_SECONDS,
            21_600,
            "seconds",
            "Focused Day",
            "Code 6 hours in a single day"),
    DAILY_BURST(
            AchievementType.MAX_DAILY_SECONDS,
            28_800,
            "seconds",
            "Sprint",
            "Code more than 8 hours in a single day"),
    DAILY_BURST_10(
            AchievementType.MAX_DAILY_SECONDS,
            36_000,
            "seconds",
            "Endurance Run",
            "Code 10 hours in a single day"),
    DAILY_BURST_12(
            AchievementType.MAX_DAILY_SECONDS,
            43_200,
            "seconds",
            "Iron Day",
            "Code 12 hours in a single day"),

    PERFECT_MONTH_50(
            AchievementType.PERFECT_MONTH,
            50,
            "percent",
            "Steady Month",
            "Code on half of a calendar month's days"),
    PERFECT_MONTH_70(
            AchievementType.PERFECT_MONTH,
            70,
            "percent",
            "Regular Month",
            "Code on 70% of a calendar month's days"),
    PERFECT_MONTH_90(
            AchievementType.PERFECT_MONTH,
            90,
            "percent",
            "Consistent Month",
            "Code on 90% of a calendar month's days"),
    PERFECT_MONTH_95(
            AchievementType.PERFECT_MONTH,
            95,
            "percent",
            "Near-Perfect Month",
            "Code on 95% of a calendar month's days"),
    PERFECT_MONTH(
            AchievementType.PERFECT_MONTH,
            100,
            "percent",
            "Perfect Month",
            "Code on every day of a calendar month");

    /**
     * Tier ordinal within the badge's own ladder, 1-based.
     *
     * <p>Derived from {@link #values()} after every constant exists, so a constant carries no
     * hand-maintained ordinal that could drift when a rung is inserted. Static field initializers
     * run after the constant list, which is what makes this safe.
     */
    private static final Map<Achievement, Integer> TIERS = buildTiers();

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

    private static Map<Achievement, Integer> buildTiers() {
        Map<AchievementType, List<Achievement>> byType = new EnumMap<>(AchievementType.class);
        for (Achievement achievement : values()) {
            byType.computeIfAbsent(achievement.type, _ -> new ArrayList<>()).add(achievement);
        }
        Map<Achievement, Integer> tiers = new EnumMap<>(Achievement.class);
        for (List<Achievement> ladder : byType.values()) {
            ladder.sort(Comparator.comparingLong(Achievement::target));
            for (int index = 0; index < ladder.size(); index++) {
                tiers.put(ladder.get(index), index + 1);
            }
        }
        return tiers;
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

    /**
     * Returns this badge's 1-based position in its own type's ladder, ordered by target ascending.
     *
     * <p>Lets a client group badges by {@link #type()} and render the rung number without shipping
     * its own code-to-ladder table.
     *
     * @return the tier ordinal, starting at 1
     */
    public int tier() {
        return TIERS.get(this);
    }
}
