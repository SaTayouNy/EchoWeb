ALTER TABLE reviews ADD COLUMN title VARCHAR(200);
ALTER TABLE reviews ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE reviews ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reviews ADD COLUMN user_rating_id UUID;
ALTER TABLE reviews ALTER COLUMN rating DROP NOT NULL;
UPDATE reviews SET published_at = created_at;
-- Old API allowed independent rating deletion. Restore the original legacy scalar
-- before linking historical reviews; never manufacture five raw parameters.
INSERT INTO user_ratings
    (rating_id, user_id, media_id, media_type, tmdb_id, title, poster_path, rating, created_at, updated_at)
SELECT r.review_id, r.user_id, r.media_id, r.media_type, r.tmdb_movie_id,
       r.movie_title, r.movie_poster_path, r.rating, r.created_at, r.updated_at
FROM reviews r
WHERE NOT EXISTS (SELECT 1 FROM user_ratings ur WHERE ur.user_id = r.user_id
    AND ur.media_type = r.media_type AND ur.tmdb_id = r.tmdb_movie_id);
UPDATE reviews SET user_rating_id = (
    SELECT rating_id FROM user_ratings r
    WHERE r.user_id = reviews.user_id AND r.media_type = reviews.media_type AND r.tmdb_id = reviews.tmdb_movie_id
);
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_user_rating FOREIGN KEY (user_rating_id) REFERENCES user_ratings(rating_id) ON DELETE CASCADE;
ALTER TABLE reviews ALTER COLUMN user_rating_id SET NOT NULL;
ALTER TABLE reviews ALTER COLUMN status SET DEFAULT 'NOT_WRITTEN';
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_status CHECK (status IN ('NOT_WRITTEN', 'PENDING_MODERATION', 'PUBLISHED'));
CREATE INDEX idx_reviews_status_published ON reviews(status, published_at);
