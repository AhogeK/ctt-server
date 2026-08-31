-- =====================================================================
-- Add ACHIEVEMENT to the audit resource type constraint.
--
-- The init migration's chk_audit_resource_type constraint is NOT edited:
-- it is already applied in existing databases, so changing the file would
-- break Flyway checksum validation on startup. This migration rebuilds the
-- constraint with the new enum value instead.
-- =====================================================================

ALTER TABLE audit_logs DROP CONSTRAINT chk_audit_resource_type;

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_resource_type
        CHECK (resource_type IN (
                                 'USER',
                                 'EMAIL_VERIFICATION',
                                 'PASSWORD_RESET',
                                 'REFRESH_TOKEN',
                                 'API_KEY',
                                 'MAIL_OUTBOX',
                                 'CODING_SESSION',
                                 'DEVICE',
                                 'ACHIEVEMENT',
                                 'UNKNOWN',
                                 'OAUTH_ACCOUNT'
            ));

COMMENT ON CONSTRAINT chk_audit_resource_type ON audit_logs IS 'Validates audit log resource types - must match ResourceType enum';
