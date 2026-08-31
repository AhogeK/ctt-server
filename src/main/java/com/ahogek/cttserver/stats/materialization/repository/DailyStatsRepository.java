package com.ahogek.cttserver.stats.materialization.repository;

import com.ahogek.cttserver.stats.materialization.entity.DailyStats;
import com.ahogek.cttserver.stats.materialization.entity.DailyStats.DailyStatsId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link DailyStats} materialized table.
 *
 * <p>Reads fetch the day rows a dashboard query needs; writes upsert single days (push touches a
 * few UTC dates) or replace the whole history (lazy bootstrap). The upsert is a native {@code
 * INSERT ... ON CONFLICT} statement so concurrent materializations of the same day converge on the
 * primary key.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, DailyStatsId> {

    /** Returns the user's materialized days inside the inclusive UTC range, date ascending. */
    List<DailyStats> findByUserIdAndUtcDateBetweenOrderByUtcDateAsc(
            UUID userId, LocalDate start, LocalDate end);

    /** Returns every materialized day for the user, date ascending. */
    List<DailyStats> findByUserIdOrderByUtcDateAsc(UUID userId);

    /** Returns whether any bootstrapped row exists for the user (full-history rebuild marker). */
    @Query(
            "SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM DailyStats d "
                    + "WHERE d.userId = :userId AND d.bootstrapped = true")
    boolean existsBootstrapped(@Param("userId") UUID userId);

    /** Upserts one materialized day with the overlap-collapsed seconds computed by the caller. */
    @Modifying
    @Query(
            value =
                    "INSERT INTO daily_stats (user_id, utc_date, merged_seconds, bootstrapped) "
                            + "VALUES (:userId, :utcDate, :mergedSeconds, :bootstrapped) "
                            + "ON CONFLICT (user_id, utc_date) DO UPDATE SET "
                            + "merged_seconds = EXCLUDED.merged_seconds, "
                            + "bootstrapped = EXCLUDED.bootstrapped, "
                            + "updated_at = CURRENT_TIMESTAMP",
            nativeQuery = true)
    void upsertDay(
            @Param("userId") UUID userId,
            @Param("utcDate") LocalDate utcDate,
            @Param("mergedSeconds") long mergedSeconds,
            @Param("bootstrapped") boolean bootstrapped);

    /** Deletes all materialized rows for the user (bootstrap recompute path). */
    @Modifying
    @Query(value = "DELETE FROM daily_stats WHERE user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
