ALTER TABLE sessions
    ADD COLUMN token_family_id UUID;

ALTER TABLE sessions
    ADD COLUMN rotated_from_session_id UUID;

ALTER TABLE sessions
    ADD COLUMN refresh_used_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sessions
    ADD COLUMN compromised_at TIMESTAMP WITH TIME ZONE;

UPDATE sessions
SET token_family_id = id
WHERE token_family_id IS NULL;

ALTER TABLE sessions
    ALTER COLUMN token_family_id SET NOT NULL;

CREATE INDEX idx_sessions_token_family_id ON sessions (token_family_id);

ALTER TABLE sessions
    ADD CONSTRAINT fk_sessions_rotated_from_session
        FOREIGN KEY (rotated_from_session_id) REFERENCES sessions (id) ON DELETE SET NULL;
