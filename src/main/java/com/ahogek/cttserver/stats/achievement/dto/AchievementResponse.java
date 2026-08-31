package com.ahogek.cttserver.stats.achievement.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One achievement badge with its unlock state and progress.
 *
 * @param code the stable achievement code (also the unlock-record key)
 * @param displayName human-readable badge name
 * @param description what the badge rewards
 * @param unlocked whether the badge is currently unlocked
 * @param unlockedAt when it was unlocked, or {@code null} when not unlocked
 * @param progress the user's current value for this achievement (same unit as {@code target})
 * @param target the threshold the badge unlocks at
 * @param unit the unit of {@code progress} and {@code target} (seconds / days / languages / month)
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Schema(description = "An achievement badge with unlock state and progress")
public record AchievementResponse(
        @Schema(description = "Stable achievement code", example = "STREAK_7") String code,
        @Schema(description = "Human-readable badge name", example = "7-Day Streak")
                String displayName,
        @Schema(description = "What the badge rewards", example = "Code on 7 consecutive days")
                String description,
        @Schema(description = "Whether the badge is unlocked", example = "true") boolean unlocked,
        @Schema(description = "Unlock time, or null when not unlocked", nullable = true)
                Instant unlockedAt,
        @Schema(description = "Current progress value", example = "7") long progress,
        @Schema(description = "Threshold the badge unlocks at", example = "7") long target,
        @Schema(description = "Unit of progress and target", example = "days") String unit) {}
