-- Rationale: The devices table is designed for IDE plugin clients (ide_version, ide_name, platform).
-- WEB login should not require a pre-existing device record.
-- device_id remains as an optional tracking field without foreign key enforcement.

ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_device_id_fkey;
