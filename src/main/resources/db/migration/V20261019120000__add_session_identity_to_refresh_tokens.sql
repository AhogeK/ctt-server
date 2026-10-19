-- Session identity for refresh tokens (phase six, R2).
--
-- A login is a session: every row that login produces — the one issued now and every row later
-- created by rotation — carries the same session_id, so "one login = one session" survives
-- refreshing. user_agent records the client that opened the session, raw and unparsed.
--
-- Additive only: the init migration is already applied everywhere and is never edited (R22).

ALTER TABLE refresh_tokens ADD COLUMN session_id UUID;
UPDATE refresh_tokens SET session_id = id WHERE session_id IS NULL;  -- each legacy row becomes its own session
ALTER TABLE refresh_tokens ALTER COLUMN session_id SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN session_id SET DEFAULT gen_random_uuid();
CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (user_id, session_id);

-- Nullable: rows written before this column existed have none, and a client may omit the header.
ALTER TABLE refresh_tokens ADD COLUMN user_agent VARCHAR(512);
