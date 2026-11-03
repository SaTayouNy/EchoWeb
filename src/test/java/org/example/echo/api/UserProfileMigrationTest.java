package org.example.echo.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileMigrationTest {
    @Test
    void v12KeepsExistingUsersAndBackfillsRatingsFromReviews() throws Exception {
        String url = "jdbc:h2:mem:profile_migration_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("11").load().migrate();

        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID legacyReviewId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.prepareStatement("""
                insert into users
                    (user_id, username, username_normalized, password_hash, role, is_active, email_verified, created_at, updated_at)
                values (?, 'ExistingUser', 'existinguser', 'hash', 'USER', true, true, current_timestamp, current_timestamp)
                """)) {
                statement.setObject(1, userId);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                insert into reviews
                    (review_id, user_id, media_id, media_type, tmdb_movie_id, movie_title, rating,
                     review_text, created_at, updated_at)
                values (?, ?, null, 'TV', 902, 'Legacy series', 7, 'Legacy review',
                        current_timestamp, current_timestamp)
                """)) {
                statement.setObject(1, legacyReviewId);
                statement.setObject(2, userId);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                insert into media
                    (media_id, media_type, tmdb_id, sync_status, title, cast_members, genres, countries,
                     last_synced_at, created_at, updated_at)
                values (?, 'MOVIE', 901, 'SYNCED', 'Existing movie', '[]', '[]', '[]',
                        current_timestamp, current_timestamp, current_timestamp)
                """)) {
                statement.setObject(1, mediaId);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                insert into reviews
                    (review_id, user_id, media_id, media_type, tmdb_movie_id, movie_title, rating,
                     review_text, created_at, updated_at)
                values (?, ?, ?, 'MOVIE', 901, 'Existing movie', 8, 'Existing review',
                        current_timestamp, current_timestamp)
                """)) {
                statement.setObject(1, reviewId);
                statement.setObject(2, userId);
                statement.setObject(3, mediaId);
                statement.executeUpdate();
            }
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.prepareStatement("""
                 select u.avatar_url, u.bio, u.preferred_genres, r.rating, r.user_id, r.media_id, r.tmdb_id
                 from users u
                 join user_ratings r on r.user_id = u.user_id
                 where u.user_id = ?
                 order by r.tmdb_id
                 """)) {
            statement.setObject(1, userId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("avatar_url")).isNull();
                assertThat(result.getString("bio")).isNull();
                assertThat(result.getString("preferred_genres")).isEqualTo("[]");
                assertThat(result.getInt("rating")).isEqualTo(8);
                assertThat(result.getObject("user_id", UUID.class)).isEqualTo(userId);
                assertThat(result.getObject("media_id", UUID.class)).isEqualTo(mediaId);
                assertThat(result.getLong("tmdb_id")).isEqualTo(901L);
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("rating")).isEqualTo(7);
                assertThat(result.getObject("media_id", UUID.class)).isNull();
                assertThat(result.getLong("tmdb_id")).isEqualTo(902L);
                assertThat(result.next()).isFalse();
            }
        }
    }
}
