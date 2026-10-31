-- One-time recovery codes for two-factor authentication.
-- Only the hash is stored; the raw code is returned once when issued.
CREATE TABLE user_recovery_codes
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    batch_id   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at    TIMESTAMPTZ,
    CONSTRAINT uq_user_recovery_codes_user_hash UNIQUE (user_id, code_hash)
);

CREATE INDEX idx_user_recovery_codes_user_unused
    ON user_recovery_codes (user_id, used_at);

COMMENT ON TABLE user_recovery_codes IS
    'Single-use recovery codes for two-factor authentication; raw values are never stored';
COMMENT ON COLUMN user_recovery_codes.code_hash IS
    'SHA-256 hash of the normalized recovery code';
COMMENT ON COLUMN user_recovery_codes.batch_id IS
    'Groups the codes issued together; regeneration replaces the previous batch';
COMMENT ON COLUMN user_recovery_codes.used_at IS
    'When the code was consumed; non-null codes cannot be used again';
