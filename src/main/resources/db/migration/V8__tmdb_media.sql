CREATE TABLE media (
    media_id UUID PRIMARY KEY,
    media_type VARCHAR(16) NOT NULL,
    tmdb_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    original_title VARCHAR(255),
    overview TEXT,
    original_overview TEXT,
    release_year INTEGER,
    release_date DATE,
    runtime_minutes INTEGER,
    poster_path VARCHAR(255),
    poster_url VARCHAR(512),
    director VARCHAR(255),
    cast_members TEXT NOT NULL DEFAULT '[]',
    genres TEXT NOT NULL DEFAULT '[]',
    countries TEXT NOT NULL DEFAULT '[]',
    last_synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_media_type_tmdb_id UNIQUE (media_type, tmdb_id)
);

CREATE INDEX idx_media_type_tmdb_id ON media (media_type, tmdb_id);
CREATE INDEX idx_media_last_synced_at ON media (last_synced_at);

ALTER TABLE reviews ADD COLUMN media_type VARCHAR(16) NOT NULL DEFAULT 'MOVIE';
ALTER TABLE reviews ADD COLUMN media_id UUID;
ALTER TABLE reviews DROP CONSTRAINT uk_reviews_user_tmdb;
ALTER TABLE reviews ADD CONSTRAINT uk_reviews_user_media_tmdb UNIQUE (user_id, media_type, tmdb_movie_id);
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_media FOREIGN KEY (media_id) REFERENCES media (media_id) ON DELETE SET NULL;

CREATE INDEX idx_reviews_media_type_tmdb_created_at ON reviews (media_type, tmdb_movie_id, created_at);
