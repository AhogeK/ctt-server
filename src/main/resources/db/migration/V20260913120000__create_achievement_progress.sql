-- =====================================================================
-- Achievement progress high-water marks, one row per (user, family).
--
-- Progress is recomputed from live coding sessions on every evaluation,
-- but sessions can be soft-deleted, which would let a reported progress
-- fall below a threshold the user already holds an unlock for. The
-- dashboard would then read "3 of 10 days" next to an awarded badge.
--
-- This table records the highest progress ever observed per family, so
-- the reported value is monotonic and an awarded badge can never be
-- contradicted by a lower number.
--
-- Unlike daily_stats this is NOT purely derived state: the maximum over
-- past observations cannot be recomputed from the current rows once the
-- sessions that produced it are deleted. It is therefore the system of
-- record for "the best this user has ever been measured at", not a cache.
--
-- Granularity is the family, not the badge: progress is a property of the
-- family (all eight streak rungs report the same measured value), so
-- storing it per badge would repeat one number eight times per user.
-- =====================================================================

CREATE TABLE achievement_progress
(
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    achievement_type VARCHAR(50) NOT NULL,
    progress         BIGINT      NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, achievement_type)
);

CREATE INDEX idx_achievement_progress_user_id ON achievement_progress (user_id);

COMMENT ON TABLE achievement_progress IS 'Highest achievement progress ever observed per user and family (monotonic; not recomputable)';
COMMENT ON COLUMN achievement_progress.user_id IS 'Owning user (cascade-deleted with the account)';
COMMENT ON COLUMN achievement_progress.achievement_type IS 'Achievement family, matching the AchievementType enum';
COMMENT ON COLUMN achievement_progress.progress IS 'Highest value ever observed for this family, in the family progress unit';
COMMENT ON COLUMN achievement_progress.updated_at IS 'When the high-water mark last increased';
