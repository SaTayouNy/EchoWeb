package org.example.echo.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredReviewMigrationTest {
    @TempDir Path directory;

    @Test
    void missingLegacyRatingIsRestoredAndCascadesWithTheOwner() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("reviews").toAbsolutePath().toString().replace('\\', '/')
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("15").load().migrate();
        UUID user = UUID.randomUUID();
        UUID review = UUID.randomUUID();
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            try (var insert = db.prepareStatement("""
                insert into users (user_id, username, username_normalized, password_hash, role,
                    is_active, email_verified, created_at, updated_at)
                values (?, 'legacyReviewer', 'legacyreviewer', 'hash', 'USER', true, true,
                    current_timestamp, current_timestamp)
                """)) {
                insert.setObject(1, user);
                insert.executeUpdate();
            }
            try (var insert = db.prepareStatement("""
                insert into reviews (review_id, user_id, tmdb_movie_id, media_type, movie_title,
                    rating, review_text, created_at, updated_at)
                values (?, ?, 902, 'MOVIE', 'Saved film', 8, 'Saved review',
                    current_timestamp, current_timestamp)
                """)) {
                insert.setObject(1, review);
                insert.setObject(2, user);
                insert.executeUpdate();
            }
            // v1 allowed this deletion even while its review still existed.
            try (var delete = db.prepareStatement("delete from user_ratings where user_id = ?")) {
                delete.setObject(1, user);
                delete.executeUpdate();
            }
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            try (var read = db.prepareStatement("""
                select r.status, r.published_at, r.user_rating_id, ur.rating, ur.scheme_version
                from reviews r join user_ratings ur on ur.rating_id = r.user_rating_id
                where r.review_id = ?
                """)) {
                read.setObject(1, review);
                try (var row = read.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("status")).isEqualTo("PUBLISHED");
                    assertThat(row.getObject("published_at")).isNotNull();
                    assertThat(row.getObject("user_rating_id", UUID.class)).isEqualTo(review);
                    assertThat(row.getInt("rating")).isEqualTo(8);
                    assertThat(row.getString("scheme_version")).isNull();
                }
            }
            try (var delete = db.prepareStatement("delete from users where user_id = ?")) {
                delete.setObject(1, user);
                delete.executeUpdate();
            }
            try (var count = db.createStatement(); var rows = count.executeQuery("select count(*) from reviews")) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
            try (var count = db.createStatement(); var rows = count.executeQuery("select count(*) from user_ratings")) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
