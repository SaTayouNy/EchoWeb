package org.example.echo.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructuredRatingMigrationTest {
    @TempDir Path directory;

    @Test
    void existingScalarSurvivesUpgradeAndRawScoresSurviveDatabaseReopen() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("ratings").toAbsolutePath().toString().replace('\\', '/')
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("14").load().migrate();
        UUID user = UUID.randomUUID(), legacy = UUID.randomUUID(), structured = UUID.randomUUID();
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            execute(db, "insert into users (user_id,username,username_normalized,password_hash,role,is_active,email_verified,created_at,updated_at) values (?, ?, ?, 'hash', 'USER', true, true, current_timestamp, current_timestamp)", user, user.toString(), user.toString());
            execute(db, "insert into user_ratings (rating_id,user_id,media_type,tmdb_id,rating,created_at,updated_at) values (?, ?, 'MOVIE', 61, 8, current_timestamp,current_timestamp)", legacy, user);
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            assertThat(count(db, "select count(*) from user_ratings where rating=8 and scheme_version is null and final_score is null")).isEqualTo(1);
            assertThat(count(db, "select count(*) from user_rating_values")).isZero();
            execute(db, "insert into user_ratings (rating_id,user_id,media_type,tmdb_id,scheme_version,final_score,created_at,updated_at) values (?, ?, 'MOVIE',62,'echo-form-impression-v1',77,current_timestamp,current_timestamp)", structured, user);
            String[] codes = {"dramaturgy_structure", "characters_performance", "visual_language", "sound_music", "impression"};
            int[] values = {9, 8, 7, 9, 9};
            for (int i = 0; i < codes.length; i++) execute(db, "insert into user_rating_values values (?, ?, ?)", structured, codes[i], values[i]);
            assertThat(assertThrows(SQLException.class, () -> execute(db, "insert into user_rating_values values (?, 'bad', 11)", structured)).getSQLState()).startsWith("23");
            assertThat(assertThrows(SQLException.class, () -> execute(db, "insert into user_rating_values values (?, 'impression', 9)", structured)).getSQLState()).isEqualTo("23505");
            assertThat(assertThrows(SQLException.class, () -> execute(db, "insert into user_ratings (rating_id,user_id,media_type,tmdb_id,rating,created_at,updated_at) values (?, ?, 'MOVIE',62,1,current_timestamp,current_timestamp)", UUID.randomUUID(), user)).getSQLState()).isEqualTo("23505");
        }
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            assertThat(count(db, "select sum(raw_value) from user_rating_values")).isEqualTo(42);
            assertThat(count(db, "select final_score from user_ratings where tmdb_id=62")).isEqualTo(77);
            assertThat(count(db, "select rating from user_ratings where tmdb_id=61")).isEqualTo(8);
            execute(db, "delete from user_ratings where rating_id=?", structured);
            assertThat(count(db, "select count(*) from user_rating_values")).isZero();
        }
    }

    private void execute(Connection db, String sql, Object... values) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }
    private long count(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }
}
