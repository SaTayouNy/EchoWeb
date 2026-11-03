CREATE TABLE password_recovery_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_password_recovery_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_recovery_tokens_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE INDEX idx_password_recovery_tokens_user_id ON password_recovery_tokens (user_id);
CREATE INDEX idx_password_recovery_tokens_expires_at ON password_recovery_tokens (expires_at);
