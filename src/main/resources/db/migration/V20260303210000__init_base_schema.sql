-- ==============================================================================
-- Migration: Init Base Schema
-- Description: Creates initial tables for CTT Server (Users, Devices, API Keys, Sync, Audit)
-- Database: PostgreSQL 16+
-- ==============================================================================

-- 1. Users table (with OAuth and audit support)
CREATE TABLE users
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE,
    display_name  VARCHAR(100),
    avatar_url    VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    password_hash VARCHAR(255),
    github_id     VARCHAR(100) UNIQUE,
    github_login  VARCHAR(100),
    last_login_at TIMESTAMPTZ,
    last_login_ip VARCHAR(50),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE users IS 'Registered users / web panel users';
COMMENT ON COLUMN users.id IS 'Unique user identifier (UUID)';
COMMENT ON COLUMN users.email IS 'User email address (unique)';
COMMENT ON COLUMN users.display_name IS 'Display name shown in UI';
COMMENT ON COLUMN users.avatar_url IS 'User avatar image URL';
COMMENT ON COLUMN users.status IS 'User status: ACTIVE, SUSPENDED, DELETED';
COMMENT ON COLUMN users.password_hash IS 'Strong hashed password using BCrypt/Argon2 (nullable for pure OAuth users)';
COMMENT ON COLUMN users.github_id IS 'Bound GitHub unique numeric ID';
COMMENT ON COLUMN users.github_login IS 'GitHub username';
COMMENT ON COLUMN users.last_login_at IS 'Last login timestamp';
COMMENT ON COLUMN users.last_login_ip IS 'Last login IP address';
COMMENT ON COLUMN users.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN users.updated_at IS 'Record last update timestamp';

-- 2. Devices table
CREATE TABLE devices
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_name  VARCHAR(255),
    platform     VARCHAR(50),
    ide_name     VARCHAR(100),
    ide_version  VARCHAR(50),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, id)
);
COMMENT ON TABLE devices IS 'Client device registration';
COMMENT ON COLUMN devices.id IS 'Unique device identifier (UUID)';
COMMENT ON COLUMN devices.user_id IS 'Reference to owning user';
COMMENT ON COLUMN devices.device_name IS 'Human-readable device name';
COMMENT ON COLUMN devices.platform IS 'Operating system platform (e.g., macOS, Windows, Linux)';
COMMENT ON COLUMN devices.ide_name IS 'IDE name (e.g., IntelliJ IDEA, VS Code)';
COMMENT ON COLUMN devices.ide_version IS 'IDE version string';
COMMENT ON COLUMN devices.created_at IS 'Device registration timestamp';
COMMENT ON COLUMN devices.last_seen_at IS 'Last activity timestamp from this device';

-- 3. API Keys table
CREATE TABLE api_keys
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id    UUID         REFERENCES devices (id) ON DELETE SET NULL,
    key_prefix   VARCHAR(32)  NOT NULL,
    key_hash     VARCHAR(255) NOT NULL,
    name         VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    UNIQUE (key_prefix),
    UNIQUE (key_hash)
);
COMMENT ON TABLE api_keys IS 'Authentication API keys for devices/applications';
COMMENT ON COLUMN api_keys.id IS 'Unique API key identifier (UUID)';
COMMENT ON COLUMN api_keys.user_id IS 'Reference to owning user';
COMMENT ON COLUMN api_keys.device_id IS 'Reference to bound device (nullable if not device-specific)';
COMMENT ON COLUMN api_keys.key_prefix IS 'First 8 chars of key for identification (e.g., ctt_xxxx)';
COMMENT ON COLUMN api_keys.key_hash IS 'BCrypt hash of the full API key';
COMMENT ON COLUMN api_keys.name IS 'Human-readable key name/description';
COMMENT ON COLUMN api_keys.created_at IS 'Key creation timestamp';
COMMENT ON COLUMN api_keys.last_used_at IS 'Last usage timestamp';
COMMENT ON COLUMN api_keys.revoked_at IS 'Revocation timestamp (null if active)';

-- 4. Coding sessions table (core business)
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
    CONSTRAINT chk_time_range CHECK (end_time >= start_time),
    UNIQUE (user_id, session_uuid)
);
COMMENT ON TABLE coding_sessions IS 'Main coding session records with duration tracking';
COMMENT ON COLUMN coding_sessions.id IS 'Unique session identifier (UUID, server-side)';
COMMENT ON COLUMN coding_sessions.user_id IS 'Reference to owning user';
COMMENT ON COLUMN coding_sessions.session_uuid IS 'Client-generated session UUID (unique per user)';
COMMENT ON COLUMN coding_sessions.project_name IS 'Project or repository name';
COMMENT ON COLUMN coding_sessions.language IS 'Primary programming language';
COMMENT ON COLUMN coding_sessions.start_time IS 'Session start time';
COMMENT ON COLUMN coding_sessions.end_time IS 'Session end time';
COMMENT ON COLUMN coding_sessions.client_modified_at IS 'Last modification timestamp from client device';
COMMENT ON COLUMN coding_sessions.client_version IS 'Client-side version counter for optimistic locking';
COMMENT ON COLUMN coding_sessions.server_version IS 'Server-side version counter for sync watermark';
COMMENT ON COLUMN coding_sessions.updated_by_device_id IS 'Device that last updated this session';
COMMENT ON COLUMN coding_sessions.is_deleted IS 'Soft delete flag';
COMMENT ON COLUMN coding_sessions.deleted_at IS 'Deletion timestamp (null if not deleted)';
COMMENT ON COLUMN coding_sessions.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN coding_sessions.updated_at IS 'Record last update timestamp';

-- 5. Session changes log (sync watermark base)
CREATE TABLE session_changes
(
    change_id      BIGSERIAL PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id      UUID        REFERENCES devices (id) ON DELETE SET NULL,
    session_id     UUID        NOT NULL REFERENCES coding_sessions (id) ON DELETE CASCADE,
    op             VARCHAR(10) NOT NULL,
    server_version BIGINT      NOT NULL,
    happened_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_op_type CHECK (op IN ('UPSERT', 'DELETE'))
);
COMMENT ON TABLE session_changes IS 'Change tracking log for full/incremental sync';
COMMENT ON COLUMN session_changes.change_id IS 'Monotonically increasing change identifier (BIGSERIAL)';
COMMENT ON COLUMN session_changes.user_id IS 'Reference to affected user';
COMMENT ON COLUMN session_changes.device_id IS 'Device that made the change';
COMMENT ON COLUMN session_changes.session_id IS 'Reference to affected session';
COMMENT ON COLUMN session_changes.op IS 'Operation type: UPSERT or DELETE';
COMMENT ON COLUMN session_changes.server_version IS 'Server version at time of change';
COMMENT ON COLUMN session_changes.happened_at IS 'Timestamp when change occurred';

-- 6. Sync cursors per device (watermark tracking)
CREATE TABLE sync_cursors
(
    user_id               UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id             UUID        NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    last_pulled_change_id BIGINT      NOT NULL DEFAULT 0,
    last_push_at          TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, device_id)
);
COMMENT ON TABLE sync_cursors IS 'Sync watermark tracking for each client device';
COMMENT ON COLUMN sync_cursors.user_id IS 'Reference to user';
COMMENT ON COLUMN sync_cursors.device_id IS 'Reference to device';
COMMENT ON COLUMN sync_cursors.last_pulled_change_id IS 'Last change_id pulled by this device (watermark)';
COMMENT ON COLUMN sync_cursors.last_push_at IS 'Timestamp of last push from this device';
COMMENT ON COLUMN sync_cursors.updated_at IS 'Record last update timestamp';

-- 7. Audit logs table
CREATE TABLE audit_logs
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID         REFERENCES users (id) ON DELETE SET NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50)  NOT NULL,
    resource_id   VARCHAR(255),
    details       JSONB,
    ip_address    VARCHAR(50),
    user_agent    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE audit_logs IS 'System security and operation audit log table';
COMMENT ON COLUMN audit_logs.id IS 'Unique audit log identifier';
COMMENT ON COLUMN audit_logs.user_id IS 'Reference to user who performed the action';
COMMENT ON COLUMN audit_logs.action IS 'Operation type, e.g.: LOGIN, GENERATE_API_KEY, REVOKE_API_KEY';
COMMENT ON COLUMN audit_logs.resource_type IS 'Type of resource being operated, e.g.: API_KEY, SESSION';
COMMENT ON COLUMN audit_logs.resource_id IS 'Identifier of the affected resource';
COMMENT ON COLUMN audit_logs.details IS 'Detailed payload data of the operation, using JSONB for flexible queries';
COMMENT ON COLUMN audit_logs.ip_address IS 'Client IP address';
COMMENT ON COLUMN audit_logs.user_agent IS 'Client user agent string';
COMMENT ON COLUMN audit_logs.created_at IS 'Timestamp when the action occurred';

-- ==============================================================================
-- Indexes
-- ==============================================================================

-- Time range aggregation query index (filtering deleted data)
CREATE INDEX idx_sessions_user_time
    ON coding_sessions (user_id, start_time, end_time)
    WHERE is_deleted = FALSE;

-- Fast lookup for device change logs
CREATE INDEX idx_session_changes_user_change
    ON session_changes (user_id, change_id);

-- Audit log indexes for log analysis acceleration
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
-- Use PostgreSQL GIN index for JSONB internal query acceleration
CREATE INDEX idx_audit_logs_details ON audit_logs USING GIN (details);
