-- Tie outbox rows to their account so account deletion removes queued and historical mail.
-- Legacy rows are backfilled only where biz_id is an actual user id; other business ids stay null.
ALTER TABLE mail_outbox
    ADD COLUMN user_id UUID;

UPDATE mail_outbox m
SET user_id = m.biz_id
WHERE m.biz_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM users u WHERE u.id = m.biz_id);

ALTER TABLE mail_outbox
    ADD CONSTRAINT fk_mail_outbox_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_mail_outbox_user ON mail_outbox (user_id);
