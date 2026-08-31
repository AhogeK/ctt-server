-- Add origin-device attribution to coding sessions for per-device statistics.
--
-- updated_by_device_id tracks the LAST writer (mutated on every cross-device
-- push), so it cannot serve as a stable origin dimension. origin_device_id is
-- stamped once at session creation from the pushing device and never rewritten.
-- Backfill approximates the origin with the last-writer device (exact for
-- single-device users; best-effort for multi-device history).
-- R22: independent migration; init schema untouched.

ALTER TABLE coding_sessions
    ADD COLUMN origin_device_id UUID REFERENCES devices (id) ON DELETE SET NULL;

UPDATE coding_sessions
SET origin_device_id = updated_by_device_id
WHERE origin_device_id IS NULL;

CREATE INDEX idx_sessions_user_origin
    ON coding_sessions (user_id, origin_device_id)
    WHERE is_deleted = FALSE;

COMMENT ON COLUMN coding_sessions.origin_device_id IS
    'Device that originally pushed this session (stamped at creation, never rewritten)';
