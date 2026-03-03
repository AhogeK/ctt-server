-- ==============================================================================
-- Migration: Init Base Schema
-- Description: Creates initial tables for CTT Server (Users, Devices, API Keys, Sync)
-- Database: PostgreSQL 16+
-- ==============================================================================

-- 1. Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE,
    display_name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE users IS 'Registered users / web panel users';

-- 2. Devices table
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name VARCHAR(255),
    platform VARCHAR(50),
    ide_name VARCHAR(100),
    ide_version VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, id)
);
COMMENT ON TABLE devices IS 'Client device registration';

-- 3. API Keys table
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    key_prefix VARCHAR(32) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    UNIQUE (key_prefix),
    UNIQUE (key_hash)
);
COMMENT ON TABLE api_keys IS 'Authentication API keys for devices/applications';

-- 4. Coding sessions table (core business)
CREATE TABLE coding_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_uuid UUID NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    language VARCHAR(50) NOT NULL,
    
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    
    client_modified_at TIMESTAMPTZ NOT NULL,
    client_version INTEGER NOT NULL DEFAULT 0,
    server_version BIGINT NOT NULL DEFAULT 0,
    updated_by_device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_time_range CHECK (end_time >= start_time),
    UNIQUE (user_id, session_uuid)
);
COMMENT ON TABLE coding_sessions IS 'Main coding session records with duration tracking';

-- 5. Session changes log (sync watermark base)
CREATE TABLE session_changes (
    change_id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    session_id UUID NOT NULL REFERENCES coding_sessions(id) ON DELETE CASCADE,
    op VARCHAR(10) NOT NULL,
    server_version BIGINT NOT NULL,
    happened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_op_type CHECK (op IN ('UPSERT', 'DELETE'))
);
COMMENT ON TABLE session_changes IS 'Change tracking log for full/incremental sync';

-- 6. Sync cursors per device (watermark tracking)
CREATE TABLE sync_cursors (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    last_pulled_change_id BIGINT NOT NULL DEFAULT 0,
    last_push_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, device_id)
);
COMMENT ON TABLE sync_cursors IS 'Sync watermark tracking for each client device';

-- ==============================================================================
-- 🚀 Indexes
-- ==============================================================================

-- Time range aggregation query index (filtering deleted data)
CREATE INDEX idx_sessions_user_time 
    ON coding_sessions(user_id, start_time, end_time) 
    WHERE is_deleted = FALSE;

-- Fast lookup for device change logs
CREATE INDEX idx_session_changes_user_change 
    ON session_changes(user_id, change_id);
