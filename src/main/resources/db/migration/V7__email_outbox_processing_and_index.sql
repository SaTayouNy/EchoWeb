ALTER TABLE email_outbox
    ADD COLUMN processing_started_at TIMESTAMP WITH TIME ZONE;

DROP INDEX idx_email_outbox_status_next_attempt_at;

CREATE INDEX idx_email_outbox_dispatch
    ON email_outbox (status, next_attempt_at, created_at);
