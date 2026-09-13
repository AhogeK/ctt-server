-- =====================================================================
-- Windowed (periodic) achievements.
--
-- Lifetime badges are exhaustible: once every rung is taken there is
-- nothing left to reach. Windowed badges reset with each period, so the
-- same goal is available again next day, week, month or year.
--
-- A windowed unlock is only unique per period, so the "one row per user
-- per badge" constraint becomes a triple including the period key. That
-- is what lets a badge be earned again next period: the previous row
-- stays as history while the new period's key does not conflict.
--
-- period_key is NOT NULL with a 'LIFETIME' default, so every existing
-- row keeps exactly one unlock (the old behaviour) and lifetime badges
-- need no period bookkeeping.
-- =====================================================================

ALTER TABLE user_achievements
    ADD COLUMN period_key VARCHAR(20) NOT NULL DEFAULT 'LIFETIME';

COMMENT ON COLUMN user_achievements.period_key IS
    'Period the unlock belongs to (LIFETIME, yyyy-MM-dd, yyyy-Www, yyyy-MM or yyyy); a windowed badge is awarded at most once per period';

ALTER TABLE user_achievements
    DROP CONSTRAINT uk_user_achievements_user_code;

ALTER TABLE user_achievements
    ADD CONSTRAINT uk_user_achievements_user_code_period
        UNIQUE (user_id, achievement_code, period_key);

COMMENT ON CONSTRAINT uk_user_achievements_user_code_period ON user_achievements IS
    'At most one unlock record per user, achievement and period';

-- Period history is read per user and period when matching the current window's unlocks.
CREATE INDEX idx_user_achievements_user_period
    ON user_achievements (user_id, period_key);
