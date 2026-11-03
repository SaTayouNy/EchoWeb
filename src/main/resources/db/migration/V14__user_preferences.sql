CREATE TABLE user_preferences (
    preference_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(16) NOT NULL,
    tmdb_id BIGINT NOT NULL,
    media_id UUID,
    person_name TEXT,
    profile_path TEXT,
    profile_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_preferences_media FOREIGN KEY (media_id) REFERENCES media (media_id),
    CONSTRAINT uk_user_preferences_target UNIQUE (user_id, type, tmdb_id),
    CONSTRAINT ck_user_preferences_tmdb_id CHECK (tmdb_id > 0),
    CONSTRAINT ck_user_preferences_target CHECK (
        (type IN ('MOVIE', 'TV') AND media_id IS NOT NULL AND person_name IS NULL)
        OR (type IN ('ACTOR', 'DIRECTOR') AND media_id IS NULL AND person_name IS NOT NULL)
    )
);

CREATE INDEX idx_user_preferences_user_created ON user_preferences (user_id, created_at, preference_id);
CREATE INDEX idx_user_preferences_user_type_created ON user_preferences (user_id, type, created_at, preference_id);
CREATE INDEX idx_user_preferences_media ON user_preferences (media_id);
