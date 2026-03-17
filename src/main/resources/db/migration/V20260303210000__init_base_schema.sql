-- ==============================================================================
-- Migration: Init CTT Platform Schema
-- Description: Creates initial tables for CTT Server (Auth, Email Verification,
--              OAuth, API Keys, Sync, Audit, Mail Outbox)
-- Database: PostgreSQL 16+
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Extensions
-- ------------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------------------------------------------------------
-- Utility Function: updated_at trigger
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ------------------------------------------------------------------------------
-- 1. Users table
-- ------------------------------------------------------------------------------
CREATE TABLE users
(
    id                    UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    email                 VARCHAR(255) NOT NULL,
    display_name          VARCHAR(100) NOT NULL,
    avatar_url            VARCHAR(500),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    password_hash         VARCHAR(255),
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at     TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    last_login_ip         VARCHAR(45),
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'SUSPENDED', 'DELETED'))
);

COMMENT ON TABLE users IS 'Registered end users of the web application';
COMMENT ON COLUMN users.id IS 'Unique user identifier (UUID)';
COMMENT ON COLUMN users.email IS 'User email address';
COMMENT ON COLUMN users.display_name IS 'Display name shown in UI';
COMMENT ON COLUMN users.avatar_url IS 'User avatar image URL';
COMMENT ON COLUMN users.status IS 'User status: PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED';
COMMENT ON COLUMN users.password_hash IS 'Strong password hash using BCrypt or Argon2';
COMMENT ON COLUMN users.email_verified IS 'Whether the email address has been verified';
COMMENT ON COLUMN users.email_verified_at IS 'Timestamp when email verification was completed';
COMMENT ON COLUMN users.last_login_at IS 'Last successful login timestamp';
COMMENT ON COLUMN users.last_login_ip IS 'Last successful login IP address (IPv4/IPv6)';
COMMENT ON COLUMN users.failed_login_attempts IS 'Consecutive failed login attempts';
COMMENT ON COLUMN users.locked_until IS 'Temporary account lock expiration time';
COMMENT ON COLUMN users.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN users.updated_at IS 'Record last update timestamp';

CREATE UNIQUE INDEX uk_users_email_lower
    ON users ((LOWER(email)));
CREATE INDEX idx_users_status
    ON users (status);

-- ------------------------------------------------------------------------------
-- 2. User OAuth accounts table
-- ------------------------------------------------------------------------------
CREATE TABLE user_oauth_accounts
(
    id                      UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider                VARCHAR(30)  NOT NULL,
    provider_user_id        VARCHAR(255) NOT NULL,
    provider_login          VARCHAR(255),
    provider_email          VARCHAR(255),
    access_token_encrypted  TEXT,
    refresh_token_encrypted TEXT,
    token_expires_at        TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_oauth_provider
        CHECK (provider IN ('GITHUB', 'GOOGLE', 'GITLAB', 'APPLE'))
);

COMMENT ON TABLE user_oauth_accounts IS 'OAuth account bindings for third-party identity providers';
COMMENT ON COLUMN user_oauth_accounts.user_id IS 'Reference to the local user account';
COMMENT ON COLUMN user_oauth_accounts.provider IS 'OAuth provider name';
COMMENT ON COLUMN user_oauth_accounts.provider_user_id IS 'Unique user identifier from the provider';
COMMENT ON COLUMN user_oauth_accounts.provider_login IS 'Provider login or username';
COMMENT ON COLUMN user_oauth_accounts.provider_email IS 'Email returned by the provider';
COMMENT ON COLUMN user_oauth_accounts.access_token_encrypted IS 'Encrypted provider access token if stored';
COMMENT ON COLUMN user_oauth_accounts.refresh_token_encrypted IS 'Encrypted provider refresh token if stored';
COMMENT ON COLUMN user_oauth_accounts.token_expires_at IS 'Expiration time of the provider access token';

CREATE UNIQUE INDEX uk_user_oauth_provider_uid
    ON user_oauth_accounts (provider, provider_user_id);
CREATE UNIQUE INDEX uk_user_oauth_user_provider
    ON user_oauth_accounts (user_id, provider);
CREATE INDEX idx_user_oauth_user_id
    ON user_oauth_accounts (user_id);

-- ------------------------------------------------------------------------------
-- 3. Devices table
-- ------------------------------------------------------------------------------
CREATE TABLE devices
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_name  VARCHAR(255),
    platform     VARCHAR(50),
    ide_name     VARCHAR(100),
    ide_version  VARCHAR(50),
    app_version  VARCHAR(50),
    last_ip      VARCHAR(45),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE devices IS 'Registered client devices';
COMMENT ON COLUMN devices.user_id IS 'Reference to owning user';
COMMENT ON COLUMN devices.device_name IS 'Human-readable device name';
COMMENT ON COLUMN devices.platform IS 'Operating system platform';
COMMENT ON COLUMN devices.ide_name IS 'IDE name';
COMMENT ON COLUMN devices.ide_version IS 'IDE version';
COMMENT ON COLUMN devices.app_version IS 'Plugin or application version';
COMMENT ON COLUMN devices.last_ip IS 'Last known device IP address (IPv4/IPv6)';
COMMENT ON COLUMN devices.created_at IS 'Device registration timestamp';
COMMENT ON COLUMN devices.last_seen_at IS 'Last activity timestamp from this device';
COMMENT ON COLUMN devices.updated_at IS 'Record last update timestamp';

CREATE INDEX idx_devices_user_id
    ON devices (user_id);
CREATE INDEX idx_devices_last_seen_at
    ON devices (last_seen_at);

-- ------------------------------------------------------------------------------
-- 4. Email verification tokens table
-- ------------------------------------------------------------------------------
CREATE TABLE email_verification_tokens
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    purpose     VARCHAR(30)  NOT NULL DEFAULT 'REGISTER_VERIFY',
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,
    sent_at     TIMESTAMPTZ,
    request_ip  VARCHAR(45),
    user_agent  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_email_verification_purpose
        CHECK (purpose IN ('REGISTER_VERIFY', 'CHANGE_EMAIL'))
);

COMMENT ON TABLE email_verification_tokens IS 'One-time email verification tokens';
COMMENT ON COLUMN email_verification_tokens.email IS 'Target email address for verification';
COMMENT ON COLUMN email_verification_tokens.token_hash IS 'SHA-256 hex digest of the raw verification token';
COMMENT ON COLUMN email_verification_tokens.purpose IS 'Token purpose: REGISTER_VERIFY or CHANGE_EMAIL';
COMMENT ON COLUMN email_verification_tokens.expires_at IS 'Token expiration timestamp';
COMMENT ON COLUMN email_verification_tokens.consumed_at IS 'Timestamp when the token was consumed';
COMMENT ON COLUMN email_verification_tokens.sent_at IS 'Timestamp when the verification email was sent';
COMMENT ON COLUMN email_verification_tokens.request_ip IS 'IP address from which the request originated (IPv4/IPv6)';
COMMENT ON COLUMN email_verification_tokens.user_agent IS 'User agent from which the request originated';

CREATE UNIQUE INDEX uk_email_verification_token_hash
    ON email_verification_tokens (token_hash);
CREATE INDEX idx_email_verification_lookup
    ON email_verification_tokens (user_id, purpose, expires_at, consumed_at);
CREATE INDEX idx_email_verification_email
    ON email_verification_tokens ((LOWER(email)));

-- ------------------------------------------------------------------------------
-- 5. Password reset tokens table
-- ------------------------------------------------------------------------------
CREATE TABLE password_reset_tokens
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,
    request_ip  VARCHAR(45),
    user_agent  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE password_reset_tokens IS 'One-time password reset tokens';
COMMENT ON COLUMN password_reset_tokens.email IS 'Target email address for password reset';
COMMENT ON COLUMN password_reset_tokens.token_hash IS 'SHA-256 hex digest of the raw reset token';
COMMENT ON COLUMN password_reset_tokens.expires_at IS 'Token expiration timestamp';
COMMENT ON COLUMN password_reset_tokens.consumed_at IS 'Timestamp when the token was consumed';
COMMENT ON COLUMN password_reset_tokens.request_ip IS 'IP address from which the request originated (IPv4/IPv6)';

CREATE UNIQUE INDEX uk_password_reset_token_hash
    ON password_reset_tokens (token_hash);
CREATE INDEX idx_password_reset_lookup
    ON password_reset_tokens (user_id, expires_at, consumed_at);
CREATE INDEX idx_password_reset_email
    ON password_reset_tokens ((LOWER(email)));

-- ------------------------------------------------------------------------------
-- 6. Refresh tokens table
-- ------------------------------------------------------------------------------
CREATE TABLE refresh_tokens
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL,
    issued_for   VARCHAR(20) NOT NULL DEFAULT 'WEB',
    device_id    UUID        REFERENCES devices (id) ON DELETE SET NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_refresh_issued_for
        CHECK (issued_for IN ('WEB', 'PLUGIN'))
);

COMMENT ON TABLE refresh_tokens IS 'Refresh tokens for session continuation';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hex digest of the raw refresh token';
COMMENT ON COLUMN refresh_tokens.issued_for IS 'Audience of the refresh token: WEB or PLUGIN';
COMMENT ON COLUMN refresh_tokens.device_id IS 'Optional bound device reference';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Refresh token expiration timestamp';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'Timestamp when the refresh token was revoked';
COMMENT ON COLUMN refresh_tokens.last_used_at IS 'Last time the refresh token was used';

CREATE UNIQUE INDEX uk_refresh_tokens_token_hash
    ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_active
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

-- ------------------------------------------------------------------------------
-- 7. API keys table
-- ------------------------------------------------------------------------------
CREATE TABLE api_keys
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id    UUID        REFERENCES devices (id) ON DELETE SET NULL,
    key_prefix   VARCHAR(32) NOT NULL,
    key_hash     VARCHAR(64) NOT NULL,
    name         VARCHAR(100),
    scopes       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE api_keys IS 'Authentication API keys for devices or applications';
COMMENT ON COLUMN api_keys.key_prefix IS 'Visible key prefix used for identification';
COMMENT ON COLUMN api_keys.key_hash IS 'SHA-256 hex digest of the full API key for deterministic lookup';
COMMENT ON COLUMN api_keys.name IS 'Human-readable key name';
COMMENT ON COLUMN api_keys.scopes IS 'Granted scopes stored as JSONB array';
COMMENT ON COLUMN api_keys.last_used_at IS 'Last usage timestamp';
COMMENT ON COLUMN api_keys.expires_at IS 'Optional expiration timestamp';
COMMENT ON COLUMN api_keys.revoked_at IS 'Revocation timestamp';
COMMENT ON COLUMN api_keys.created_at IS 'Key creation timestamp';
COMMENT ON COLUMN api_keys.updated_at IS 'Record last update timestamp';

CREATE UNIQUE INDEX uk_api_keys_key_prefix
    ON api_keys (key_prefix);
CREATE UNIQUE INDEX uk_api_keys_key_hash
    ON api_keys (key_hash);
CREATE INDEX idx_api_keys_user_id
    ON api_keys (user_id);
CREATE INDEX idx_api_keys_active
    ON api_keys (user_id, created_at)
    WHERE revoked_at IS NULL;

-- ------------------------------------------------------------------------------
-- 8. Coding sessions table
-- ------------------------------------------------------------------------------
CREATE TABLE coding_sessions
(
    id                   UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_uuid         UUID         NOT NULL,
    project_name         VARCHAR(255) NOT NULL,
    language             VARCHAR(50)  NOT NULL,
    start_time           TIMESTAMPTZ  NOT NULL,
    end_time             TIMESTAMPTZ  NOT NULL,
    client_modified_at   TIMESTAMPTZ  NOT NULL,
    client_version       INTEGER      NOT NULL DEFAULT 0,
    server_version       BIGINT       NOT NULL DEFAULT 0,
    updated_by_device_id UUID         REFERENCES devices (id) ON DELETE SET NULL,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_time_range
        CHECK (end_time >= start_time),
    CONSTRAINT uk_coding_sessions_user_session_uuid
        UNIQUE (user_id, session_uuid)
);

COMMENT ON TABLE coding_sessions IS 'Main coding session records with duration tracking';
COMMENT ON COLUMN coding_sessions.user_id IS 'Reference to owning user';
COMMENT ON COLUMN coding_sessions.session_uuid IS 'Client-generated session UUID unique per user';
COMMENT ON COLUMN coding_sessions.project_name IS 'Project or repository name';
COMMENT ON COLUMN coding_sessions.language IS 'Primary programming language';
COMMENT ON COLUMN coding_sessions.start_time IS 'Session start time';
COMMENT ON COLUMN coding_sessions.end_time IS 'Session end time';
COMMENT ON COLUMN coding_sessions.client_modified_at IS 'Last modification timestamp from the client';
COMMENT ON COLUMN coding_sessions.client_version IS 'Client-side version for optimistic conflict handling';
COMMENT ON COLUMN coding_sessions.server_version IS 'Server-side version for sync watermarking';
COMMENT ON COLUMN coding_sessions.updated_by_device_id IS 'Device that last updated this session';
COMMENT ON COLUMN coding_sessions.is_deleted IS 'Soft delete flag';
COMMENT ON COLUMN coding_sessions.deleted_at IS 'Deletion timestamp';
COMMENT ON COLUMN coding_sessions.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN coding_sessions.updated_at IS 'Record last update timestamp';

CREATE INDEX idx_sessions_user_time
    ON coding_sessions (user_id, start_time, end_time)
    WHERE is_deleted = FALSE;
CREATE INDEX idx_sessions_user_project_time
    ON coding_sessions (user_id, project_name, start_time)
    WHERE is_deleted = FALSE;
CREATE INDEX idx_sessions_user_language_time
    ON coding_sessions (user_id, language, start_time)
    WHERE is_deleted = FALSE;
CREATE INDEX idx_sessions_sync_lookup
    ON coding_sessions (user_id, server_version);

-- ------------------------------------------------------------------------------
-- 9. Session changes table
-- ------------------------------------------------------------------------------
CREATE TABLE session_changes
(
    change_id      BIGSERIAL PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id      UUID        REFERENCES devices (id) ON DELETE SET NULL,
    session_id     UUID        NOT NULL REFERENCES coding_sessions (id) ON DELETE CASCADE,
    op             VARCHAR(10) NOT NULL,
    server_version BIGINT      NOT NULL,
    happened_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_session_change_op
        CHECK (op IN ('UPSERT', 'DELETE'))
);

COMMENT ON TABLE session_changes IS 'Change tracking log for incremental synchronization';
COMMENT ON COLUMN session_changes.change_id IS 'Monotonically increasing change identifier';
COMMENT ON COLUMN session_changes.user_id IS 'Reference to affected user';
COMMENT ON COLUMN session_changes.device_id IS 'Device that originated the change';
COMMENT ON COLUMN session_changes.session_id IS 'Reference to affected coding session';
COMMENT ON COLUMN session_changes.op IS 'Operation type: UPSERT or DELETE';
COMMENT ON COLUMN session_changes.server_version IS 'Server version at the moment of change';
COMMENT ON COLUMN session_changes.happened_at IS 'Timestamp when the change occurred';

CREATE INDEX idx_session_changes_user_change
    ON session_changes (user_id, change_id);
CREATE INDEX idx_session_changes_session_id
    ON session_changes (session_id);

-- ------------------------------------------------------------------------------
-- 10. Sync cursors table
-- ------------------------------------------------------------------------------
CREATE TABLE sync_cursors
(
    user_id               UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id             UUID        NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    last_pulled_change_id BIGINT      NOT NULL DEFAULT 0,
    last_push_at          TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, device_id)
);

COMMENT ON TABLE sync_cursors IS 'Synchronization watermark tracking per client device';
COMMENT ON COLUMN sync_cursors.user_id IS 'Reference to user';
COMMENT ON COLUMN sync_cursors.device_id IS 'Reference to device';
COMMENT ON COLUMN sync_cursors.last_pulled_change_id IS 'Last pulled change watermark';
COMMENT ON COLUMN sync_cursors.last_push_at IS 'Timestamp of last push from the device';
COMMENT ON COLUMN sync_cursors.updated_at IS 'Record last update timestamp';

-- ------------------------------------------------------------------------------
-- 11. Audit logs table
-- ------------------------------------------------------------------------------
CREATE TABLE audit_logs
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID         REFERENCES users (id) ON DELETE SET NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50)  NOT NULL,
    resource_id   VARCHAR(255),
    severity      VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    details       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ip_address    VARCHAR(45),
    user_agent    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_audit_resource_type
        CHECK (resource_type IN ('USER', 'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'REFRESH_TOKEN', 'API_KEY', 'UNKNOWN'))
);

COMMENT ON TABLE audit_logs IS 'System security and operational audit logs';
COMMENT ON COLUMN audit_logs.user_id IS 'Reference to user who performed the action';
COMMENT ON COLUMN audit_logs.action IS 'Operation type (e.g., LOGIN_FAILED, UNAUTHORIZED_ACCESS)';
COMMENT ON COLUMN audit_logs.resource_type IS 'Type of affected resource (Must match enum ResourceType)';
COMMENT ON COLUMN audit_logs.resource_id IS 'Identifier of the affected resource';
COMMENT ON COLUMN audit_logs.severity IS 'Security severity level: INFO, WARNING, CRITICAL';
COMMENT ON COLUMN audit_logs.details IS 'Additional structured audit payload stored as JSONB';
COMMENT ON COLUMN audit_logs.ip_address IS 'Client IP address (IPv4/IPv6)';
COMMENT ON COLUMN audit_logs.user_agent IS 'Client user agent';
COMMENT ON COLUMN audit_logs.created_at IS 'Timestamp when the action occurred';

CREATE INDEX idx_audit_logs_user_id
    ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action
    ON audit_logs (action);
CREATE INDEX idx_audit_logs_severity
    ON audit_logs (severity);
CREATE INDEX idx_audit_logs_created_at
    ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_details
    ON audit_logs USING GIN (details);

-- ------------------------------------------------------------------------------
-- 12. Mail outbox table
-- ------------------------------------------------------------------------------
CREATE TABLE mail_outbox
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    biz_type      VARCHAR(32)  NOT NULL,
    biz_id        UUID,
    recipient     VARCHAR(255) NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    template_code VARCHAR(64)  NOT NULL,
    payload       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    sent_at       TIMESTAMPTZ,
    last_error    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mail_outbox_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
);

COMMENT ON TABLE mail_outbox IS 'Transactional outbox for asynchronous email delivery';
COMMENT ON COLUMN mail_outbox.biz_type IS 'Business type such as REGISTER_VERIFY or RESET_PASSWORD';
COMMENT ON COLUMN mail_outbox.biz_id IS 'Related business entity identifier';
COMMENT ON COLUMN mail_outbox.recipient IS 'Target email recipient';
COMMENT ON COLUMN mail_outbox.subject IS 'Email subject';
COMMENT ON COLUMN mail_outbox.template_code IS 'Email template code';
COMMENT ON COLUMN mail_outbox.payload IS 'Template rendering payload';
COMMENT ON COLUMN mail_outbox.status IS 'Delivery status: PENDING, SENDING, SENT, FAILED';
COMMENT ON COLUMN mail_outbox.retry_count IS 'Number of delivery retries';
COMMENT ON COLUMN mail_outbox.next_retry_at IS 'Next scheduled retry timestamp';
COMMENT ON COLUMN mail_outbox.sent_at IS 'Timestamp when the email was sent';
COMMENT ON COLUMN mail_outbox.last_error IS 'Last delivery error message';

CREATE INDEX idx_mail_outbox_dispatch
    ON mail_outbox (status, next_retry_at, created_at);

-- ------------------------------------------------------------------------------
-- updated_at triggers
-- ------------------------------------------------------------------------------
CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_user_oauth_accounts_set_updated_at
    BEFORE UPDATE
    ON user_oauth_accounts
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_devices_set_updated_at
    BEFORE UPDATE
    ON devices
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_api_keys_set_updated_at
    BEFORE UPDATE
    ON api_keys
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_coding_sessions_set_updated_at
    BEFORE UPDATE
    ON coding_sessions
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_sync_cursors_set_updated_at
    BEFORE UPDATE
    ON sync_cursors
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_mail_outbox_set_updated_at
    BEFORE UPDATE
    ON mail_outbox
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
