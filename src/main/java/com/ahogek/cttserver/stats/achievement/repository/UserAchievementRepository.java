package com.ahogek.cttserver.stats.achievement.repository;

import com.ahogek.cttserver.stats.achievement.entity.UserAchievement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link UserAchievement} unlock records.
 *
 * <p>The {@code insertIfAbsent} write is the idempotency boundary: it relies on the {@code
 * uk_user_achievements_user_code} unique constraint via {@code INSERT ... ON CONFLICT DO NOTHING},
 * so concurrent lazy evaluations cannot double-award a badge.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    /**
     * Finds the codes a user has already unlocked.
     *
     * @param userId the owning user
     * @return the unlocked achievement codes
     */
    List<UserAchievement> findByUserId(UUID userId);

    /**
     * Inserts an unlock record atomically, skipping when the user already holds it for that period.
     *
     * <p>{@code unlockedAt} is the instant the badge was earned, computed from the session history
     * by the caller; the database no longer stamps it, because a lazily evaluated unlock would
     * otherwise record the moment the user happened to open the page.
     *
     * <p>{@code periodKey} makes a windowed badge re-earnable: the unique key includes it, so next
     * period's attempt is a new row rather than a conflict. Lifetime badges always pass {@code
     * LIFETIME} and therefore still yield at most one row.
     *
     * @param userId the owning user
     * @param achievementCode the achievement to unlock
     * @param periodKey the period the unlock belongs to
     * @param unlockedAt the instant the badge was earned
     * @return 1 when newly inserted, 0 when already unlocked for that period
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO user_achievements (user_id, achievement_code, period_key, unlocked_at)
                    VALUES (:userId, :achievementCode, :periodKey, :unlockedAt)
                    ON CONFLICT (user_id, achievement_code, period_key) DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId,
            @Param("achievementCode") String achievementCode,
            @Param("periodKey") String periodKey,
            @Param("unlockedAt") OffsetDateTime unlockedAt);
}
