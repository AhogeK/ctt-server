-- Drop schema objects with no production caller.
--
-- Each drop below was audited against every repository, JPQL query and raw statement in
-- src/main/java before being written here:
--
--   * idx_sessions_user_project_time / idx_sessions_user_language_time — no query filters or
--     orders coding_sessions by project_name or language. Statistics aggregate in memory
--     (StatsCalculator) over a user's sessions, so the database never needs those access paths.
--     They are partial indexes (WHERE is_deleted = FALSE) on a table whose index space they made
--     up roughly 47% of.
--   * idx_sessions_sync_lookup — only findAllByUserIdAndServerVersionGreaterThanAndIsDeletedFalse
--     could use it, and that method has no production caller: the push path resolves sessions by
--     session_uuid (uk_coding_sessions_user_session_uuid), not by a server-version watermark.
--   * sync_cursors.last_push_at — written by nobody and read by nobody. The push path records its
--     watermark in last_pulled_change_id; this column was left behind when that became the only
--     cursor that mattered.
--
-- The repository method and the entity field that went with them are removed in the same change
-- as this migration, so schema and code stay in step (the context validates mappings on startup).

DROP INDEX IF EXISTS idx_sessions_user_project_time;

DROP INDEX IF EXISTS idx_sessions_user_language_time;

DROP INDEX IF EXISTS idx_sessions_sync_lookup;

ALTER TABLE sync_cursors DROP COLUMN IF EXISTS last_push_at;
