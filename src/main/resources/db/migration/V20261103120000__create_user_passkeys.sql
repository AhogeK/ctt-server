-- Registered WebAuthn credentials.
-- A credential ID is globally unique; the user index supports listing and management.
CREATE TABLE user_passkeys
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    credential_id   VARCHAR(1364) NOT NULL,
    public_key_cose BYTEA        NOT NULL,
    sign_count      BIGINT       NOT NULL DEFAULT 0,
    transports      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    aaguid          UUID,
    backup_eligible BOOLEAN      NOT NULL DEFAULT FALSE,
    backup_state    BOOLEAN      NOT NULL DEFAULT FALSE,
    name            VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMPTZ,
    CONSTRAINT uq_user_passkeys_credential UNIQUE (credential_id)
);

CREATE INDEX idx_user_passkeys_user ON user_passkeys (user_id);

COMMENT ON TABLE user_passkeys IS
    'WebAuthn credentials; the raw credential ID is public, the private key never reaches the server';
COMMENT ON COLUMN user_passkeys.credential_id IS
    'Base64url credential ID from the authenticator';
COMMENT ON COLUMN user_passkeys.public_key_cose IS
    'COSE public key bytes used to verify assertions';
COMMENT ON COLUMN user_passkeys.sign_count IS
    'Last observed authenticator signature counter; zero is valid for synced credentials';
COMMENT ON COLUMN user_passkeys.transports IS
    'JSON array of authenticator transports reported at registration';
COMMENT ON COLUMN user_passkeys.backup_eligible IS
    'WebAuthn BE flag, stored for display only';
COMMENT ON COLUMN user_passkeys.backup_state IS
    'WebAuthn BS flag, stored for display only';
