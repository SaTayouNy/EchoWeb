ALTER TABLE users ADD COLUMN avatar_url VARCHAR(1024);
ALTER TABLE users ADD COLUMN bio TEXT;
ALTER TABLE users ADD COLUMN preferred_genres TEXT NOT NULL DEFAULT '[]';
ALTER TABLE users ADD COLUMN preferred_countries TEXT NOT NULL DEFAULT '[]';
ALTER TABLE users ADD COLUMN preferred_media_types TEXT NOT NULL DEFAULT '[]';

CREATE TABLE user_ratings (
    rating_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    media_id UUID,
    media_type VARCHAR(16) NOT NULL,
    tmdb_id BIGINT NOT NULL,
    title VARCHAR(255),
    poster_path VARCHAR(255),
    rating INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_ratings_rating CHECK (rating BETWEEN 1 AND 10),
    CONSTRAINT uk_user_ratings_user_media UNIQUE (user_id, media_type, tmdb_id),
    CONSTRAINT fk_user_ratings_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_ratings_media FOREIGN KEY (media_id) REFERENCES media (media_id) ON DELETE SET NULL
);

CREATE INDEX idx_user_ratings_user_updated_at ON user_ratings (user_id, updated_at);

INSERT INTO user_ratings
    (rating_id, user_id, media_id, media_type, tmdb_id, title, poster_path, rating, created_at, updated_at)
SELECT review_id, user_id, media_id, media_type, tmdb_movie_id, movie_title, movie_poster_path,
       rating, created_at, updated_at
FROM reviews;

CREATE TABLE review_likes (
    like_id UUID PRIMARY KEY,
    review_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_review_likes_review_user UNIQUE (review_id, user_id),
    CONSTRAINT fk_review_likes_review FOREIGN KEY (review_id) REFERENCES reviews (review_id) ON DELETE CASCADE,
    CONSTRAINT fk_review_likes_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE INDEX idx_review_likes_review_id ON review_likes (review_id);
CREATE INDEX idx_review_likes_user_id ON review_likes (user_id);

ALTER TABLE answers ADD COLUMN author_liked BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_answers_user_author_liked ON answers (user_id, author_liked);
