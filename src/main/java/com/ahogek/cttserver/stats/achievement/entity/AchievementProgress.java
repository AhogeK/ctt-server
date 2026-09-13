package com.ahogek.cttserver.stats.achievement.entity;

import com.ahogek.cttserver.stats.achievement.enums.AchievementType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Highest progress ever observed for one user and one achievement family.
 *
 * <p>Progress is recomputed from live sessions on every evaluation, and sessions can be soft
 * deleted, so a reported value could otherwise fall below a threshold the user already holds an
 * unlock for — the dashboard then contradicts itself. Recording the maximum makes the reported
 * value monotonic.
 *
 * <p>Unlike {@code daily_stats} this is not purely derived state: the maximum over past
 * observations cannot be rebuilt from the current sessions once the ones that produced it are
 * deleted. It is the durable record of the user's best measurement, not a cache.
 *
 * <p>Granularity is the family rather than the badge: progress is a property of the family (all
 * eight streak rungs report one measured number), so a per-badge row would repeat it eight times.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-13
 */
@Entity
@Table(name = "achievement_progress")
@IdClass(AchievementProgress.AchievementProgressId.class)
public class AchievementProgress {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "achievement_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AchievementType achievementType;

    @Column(name = "progress", nullable = false)
    private long progress;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AchievementProgress() {}

    public AchievementProgress(
            UUID userId, AchievementType achievementType, long progress, Instant updatedAt) {
        this.userId = userId;
        this.achievementType = achievementType;
        this.progress = progress;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public AchievementType getAchievementType() {
        return achievementType;
    }

    public long getProgress() {
        return progress;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Composite primary key: user + achievement family. */
    public static class AchievementProgressId implements Serializable {

        private UUID userId;
        private AchievementType achievementType;

        public AchievementProgressId() {}

        public AchievementProgressId(UUID userId, AchievementType achievementType) {
            this.userId = userId;
            this.achievementType = achievementType;
        }

        public UUID getUserId() {
            return userId;
        }

        public AchievementType getAchievementType() {
            return achievementType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AchievementProgressId that)) {
                return false;
            }
            return Objects.equals(userId, that.userId)
                    && Objects.equals(achievementType, that.achievementType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, achievementType);
        }
    }
}
