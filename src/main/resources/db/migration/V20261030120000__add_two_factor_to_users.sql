-- Two-factor authentication state on the user row.
-- All columns are nullable: existing accounts keep 2FA disabled until they confirm a secret.
ALTER TABLE users
    ADD COLUMN totp_secret_ciphertext TEXT,
    ADD COLUMN totp_confirmed_at TIMESTAMPTZ,
    ADD COLUMN totp_last_used_step BIGINT;

COMMENT ON COLUMN users.totp_secret_ciphertext IS
    'AES-GCM encrypted TOTP secret; v1:<base64 iv>:<base64 ciphertext>';
COMMENT ON COLUMN users.totp_confirmed_at IS
    'When the user confirmed the current TOTP secret; null means 2FA is disabled';
COMMENT ON COLUMN users.totp_last_used_step IS
    'Last accepted TOTP step, used to reject replay within the same time step';
