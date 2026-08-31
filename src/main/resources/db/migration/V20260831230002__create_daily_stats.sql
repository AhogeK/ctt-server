-- =====================================================================
-- Daily materialized coding statistics per user (UTC day granularity).
--
-- One row per (user, UTC date) holding the overlap-collapsed coding
-- seconds for that day. Rows are maintained incrementally: every push
-- recomputes only the UTC dates its sessions touch. A per-user
-- "bootstrapped" flag (max row) records whether the full history has
-- been materialized once; until it is set, a statistics read rebuilds
-- the whole history under a per-user lock. The table is derived state —
-- it can always be rebuilt from coding_sessions.
--
-- Timezone note: sessions are attributed to UTC days, which serves the
-- UTC statistics path (heatmap / summary totals / streaks) with zero
-- precision loss. Timezone-shifted reads keep using live aggregation.
-- =====================================================================

CREATE TABLE daily_stats
(
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    utc_date        DATE        NOT NULL,
    merged_seconds  BIGINT      NOT NULL DEFAULT 0,
    bootstrapped    BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, utc_date)
);

COMMENT ON TABLE daily_stats IS 'Per-user per-UTC-day materialized coding statistics (derived from coding_sessions)';
COMMENT ON COLUMN daily_stats.user_id IS 'Owning user (cascade-deleted with the account)';
COMMENT ON COLUMN daily_stats.utc_date IS 'UTC calendar day the coding time is attributed to (split at UTC midnight)';
COMMENT ON COLUMN daily_stats.merged_seconds IS 'Overlap-collapsed coding seconds for the UTC day';
COMMENT ON COLUMN daily_stats.bootstrapped IS 'True on rows written by the full-history rebuild; the user is considered bootstrapped while any row has it set';
COMMENT ON COLUMN daily_stats.updated_at IS 'When the materialized row was last recomputed';
