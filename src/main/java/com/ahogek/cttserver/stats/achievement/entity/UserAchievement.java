package com.ahogek.cttserver.stats.achievement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * User achievement entity representing an unlocked badge.
 *
 * <p>One row per unlocked achievement per user. The unique constraint on {@code (user_id,
 * achievement_code)} makes unlocks idempotent: lazy evaluation at query time can race and only one
 * record survives, so a badge is never double-awarded or double-audited.
 *
 * <p>{@code unlocked_at} is the instant the badge was <em>earned</em>, supplied by the writer from
 * the session history — not the instant the unlock was recorded. Evaluation is lazy, so a user who
 * reaches a target and opens the page weeks later must still see the day they reached it. It is
 * therefore no longer stamped by {@code @CreationTimestamp}; the writing path passes the value
 * computed from the same sessions that produced the progress.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Entity
@Table(name = "user_achievements")
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "achievement_code", nullable = false, length = 50)
    private String achievementCode;

    @Column(name = "unlocked_at", nullable = false, updatable = false)
    private Instant unlockedAt;

    protected UserAchievement() {}

    public UserAchievement(UUID userId, String achievementCode) {
        this.userId = userId;
        this.achievementCode = achievementCode;
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAchievementCode() {
        return achievementCode;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(Instant unlockedAt) {
        this.unlockedAt = unlockedAt;
    }
}
