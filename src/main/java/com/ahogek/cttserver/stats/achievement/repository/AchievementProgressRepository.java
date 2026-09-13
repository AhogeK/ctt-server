package com.ahogek.cttserver.stats.achievement.repository;

import com.ahogek.cttserver.stats.achievement.entity.AchievementProgress;
import com.ahogek.cttserver.stats.achievement.entity.AchievementProgress.AchievementProgressId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AchievementProgress} high-water marks.
 *
 * <p>The write is a native {@code INSERT ... ON CONFLICT} that raises the stored value only: two
 * concurrent evaluations of the same user converge on the larger measurement instead of losing one
 * to read-modify-write, and a decreased measurement never lowers the mark.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-09-13
 */
@Repository
public interface AchievementProgressRepository
        extends JpaRepository<AchievementProgress, AchievementProgressId> {

    /**
     * Returns every recorded high-water mark for the user.
     *
     * @param userId the owning user
     * @return the stored marks
     */
    List<AchievementProgress> findByUserId(UUID userId);

    /**
     * Raises the stored high-water mark for one family, inserting it when absent.
     *
     * <p>{@code GREATEST} keeps the operation monotonic under concurrency and under a measurement
     * that came back lower than a previously recorded one.
     *
     * @param userId the owning user
     * @param achievementType the family
     * @param progress the newly measured value
     * @return 1 when the stored value changed, 0 when the measurement did not exceed it
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO achievement_progress (user_id, achievement_type, progress)
                    VALUES (:userId, :achievementType, :progress)
                    ON CONFLICT (user_id, achievement_type) DO UPDATE
                    SET progress = GREATEST(achievement_progress.progress, EXCLUDED.progress),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE achievement_progress.progress < EXCLUDED.progress
                    """,
            nativeQuery = true)
    int raiseIfHigher(
            @Param("userId") UUID userId,
            @Param("achievementType") String achievementType,
            @Param("progress") long progress);

    /** Deletes every mark for the user (account-level cleanup). */
    @Modifying
    @Query(value = "DELETE FROM achievement_progress WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
