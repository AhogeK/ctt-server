package com.ahogek.cttserver.stats.materialization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Materialized per-user per-UTC-day coding statistics.
 *
 * <p>Derived state: rows are recomputed from {@code coding_sessions} on push (for the touched UTC
 * dates) and lazily bootstrapped for the full history on the first statistics read. The composite
 * id mirrors the {@code (user_id, utc_date)} primary key, following the {@code SyncCursorId}
 * pattern.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-31
 */
@Entity
@Table(name = "daily_stats")
@IdClass(DailyStats.DailyStatsId.class)
public class DailyStats {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "utc_date", nullable = false)
    private LocalDate utcDate;

    @Column(name = "merged_seconds", nullable = false)
    private long mergedSeconds;

    @Column(name = "bootstrapped", nullable = false)
    private boolean bootstrapped;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DailyStats() {}

    public DailyStats(UUID userId, LocalDate utcDate, long mergedSeconds, boolean bootstrapped) {
        this.userId = userId;
        this.utcDate = utcDate;
        this.mergedSeconds = mergedSeconds;
        this.bootstrapped = bootstrapped;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getUtcDate() {
        return utcDate;
    }

    public long getMergedSeconds() {
        return mergedSeconds;
    }

    public boolean isBootstrapped() {
        return bootstrapped;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Composite primary key: user + UTC day. */
    public static class DailyStatsId implements Serializable {

        private UUID userId;
        private LocalDate utcDate;

        public DailyStatsId() {}

        public DailyStatsId(UUID userId, LocalDate utcDate) {
            this.userId = userId;
            this.utcDate = utcDate;
        }

        public UUID getUserId() {
            return userId;
        }

        public LocalDate getUtcDate() {
            return utcDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DailyStatsId that)) {
                return false;
            }
            return Objects.equals(userId, that.userId) && Objects.equals(utcDate, that.utcDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, utcDate);
        }
    }
}
