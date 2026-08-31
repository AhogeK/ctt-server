-- =====================================================================
-- User achievements: unlocked badges for coding activity milestones.
--
-- Unlock records are idempotent: the unique constraint on
-- (user_id, achievement_code) guarantees a badge is unlocked at most
-- once even under concurrent lazy-evaluation requests.
-- =====================================================================

CREATE TABLE user_achievements (
    id               BIGSERIAL PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    achievement_code VARCHAR(50) NOT NULL,
    unlocked_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_achievements_user_code UNIQUE (user_id, achievement_code)
);

CREATE INDEX idx_user_achievements_user_id ON user_achievements (user_id);

COMMENT ON TABLE user_achievements IS 'Badges a user has unlocked, one row per achievement';
COMMENT ON COLUMN user_achievements.user_id IS 'Owning user (cascade-deleted with the account)';
COMMENT ON COLUMN user_achievements.achievement_code IS 'Stable achievement identifier matching the Achievement enum';
COMMENT ON COLUMN user_achievements.unlocked_at IS 'When the badge was unlocked';
COMMENT ON CONSTRAINT uk_user_achievements_user_code ON user_achievements IS 'At most one unlock record per user per achievement';
