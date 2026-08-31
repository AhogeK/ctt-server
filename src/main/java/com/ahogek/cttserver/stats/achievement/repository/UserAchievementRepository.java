package com.ahogek.cttserver.stats.achievement.repository;

import com.ahogek.cttserver.stats.achievement.entity.UserAchievement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Inserts an unlock record atomically, skipping when the pair already exists.
     *
     * @param userId the owning user
     * @param achievementCode the achievement to unlock
     * @return 1 when newly inserted, 0 when already unlocked
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO user_achievements (user_id, achievement_code)
                    VALUES (:userId, :achievementCode)
                    ON CONFLICT (user_id, achievement_code) DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId, @Param("achievementCode") String achievementCode);
}
