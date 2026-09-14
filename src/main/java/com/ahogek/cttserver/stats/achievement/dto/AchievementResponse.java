package com.ahogek.cttserver.stats.achievement.dto;

import java.time.Instant;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One achievement badge with its unlock state and progress.
 *
 * <p>{@code type} and {@code tier} let a client group the flat badge list into ladders without
 * shipping its own code-to-family table: grouping by {@code type} and ordering by {@code tier}
 * reproduces the server's own ladder, so a newly shipped rung appears in place with no client
 * change.
 *
 * @param code the stable achievement code (also the unlock-record key)
 * @param type the achievement family that computes this badge's progress
 * @param tier 1-based position within the family's ladder, ordered by ascending target
 * @param displayName human-readable badge name
 * @param description what the badge rewards
 * @param unlocked whether the badge is currently unlocked
 * @param unlockedAt when it was unlocked, or {@code null} when not unlocked
 * @param progress the user's current value for this achievement (same unit as {@code target})
 * @param target the threshold the badge unlocks at
 * @param unit the unit of {@code progress} and {@code target} (seconds / days / languages /
 *     percent)
 * @param window the measurement window: {@code LIFETIME}, {@code DAY}, {@code WEEK}, {@code MONTH}
 *     or {@code YEAR}
 * @param windowStart first local date of the current window, or {@code null} for {@code LIFETIME}
 * @param windowEnd last local date of the current window, or {@code null} for {@code LIFETIME}
 * @param totalUnlocks how many periods this badge has been attained in, including the current one
 *     when unlocked; always 0 or 1 for a {@code LIFETIME} badge, which has a single period
 * @param periodStreak how many consecutive periods ending with the current one the badge has been
 *     attained in; 0 when the current period is not attained, and always 0 for a {@code LIFETIME}
 *     badge
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Schema(description = "An achievement badge with unlock state and progress")
public record AchievementResponse(
        @Schema(description = "Stable achievement code", example = "STREAK_7") String code,
        @Schema(
                        description = "Achievement family that computes this badge's progress",
                        example = "STREAK")
                String type,
        @Schema(
                        description =
                                "1-based position within the family's ladder, by ascending target",
                        example = "2")
                int tier,
        @Schema(description = "Human-readable badge name", example = "7-Day Streak")
                String displayName,
        @Schema(description = "What the badge rewards", example = "Code on 7 consecutive days")
                String description,
        @Schema(description = "Whether the badge is unlocked", example = "true") boolean unlocked,
        @Schema(description = "Unlock time, or null when not unlocked", nullable = true)
                Instant unlockedAt,
        @Schema(description = "Current progress value", example = "7") long progress,
        @Schema(description = "Threshold the badge unlocks at", example = "7") long target,
        @Schema(description = "Unit of progress and target", example = "days") String unit,
        @Schema(
                        description =
                                "Measurement window; LIFETIME badges never reset, windowed badges do",
                        example = "LIFETIME")
                String window,
        @Schema(
                        description = "First local date of the current window; null for LIFETIME",
                        nullable = true,
                        example = "2026-09-07")
                LocalDate windowStart,
        @Schema(
                        description = "Last local date of the current window; null for LIFETIME",
                        nullable = true,
                        example = "2026-09-13")
                LocalDate windowEnd,
        @Schema(
                        description =
                                "How many periods this badge has been attained in, including the"
                                        + " current one when unlocked (0 or 1 for LIFETIME badges)",
                        example = "12")
                int totalUnlocks,
        @Schema(
                        description =
                                "How many consecutive periods ending with the current one it has been"
                                        + " attained in; 0 when the current period is not attained"
                                        + " and always 0 for LIFETIME badges",
                        example = "5")
                int periodStreak) {}
