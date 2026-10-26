-- Session-level IDE attribution.
--
-- A device id is installation-scoped: every JetBrains IDE on one machine shares
-- it, so devices.ide_name records whichever IDE registered last and cannot say
-- which IDE produced a given session. The plugin already reports the IDE with
-- each session; these columns store it so statistics attribute sessions to the
-- IDE that produced them.
--
-- Both columns are nullable on purpose: rows pushed by older plugins, and rows
-- that predate this migration, carry no session-level IDE. Read paths fall back
-- to the origin device's registration for those, so no backfill is attempted —
-- an inferred IDE would be a guess recorded as fact.
-- R22: independent migration; init schema untouched.

ALTER TABLE coding_sessions
    ADD COLUMN ide_name VARCHAR(100),
    ADD COLUMN ide_version VARCHAR(50);

CREATE INDEX idx_sessions_user_ide
    ON coding_sessions (user_id, ide_name)
    WHERE is_deleted = FALSE;

COMMENT ON COLUMN coding_sessions.ide_name IS
    'IDE that produced this session (nullable; readers fall back to the origin device registration)';
COMMENT ON COLUMN coding_sessions.ide_version IS
    'IDE build version that produced this session (nullable)';
