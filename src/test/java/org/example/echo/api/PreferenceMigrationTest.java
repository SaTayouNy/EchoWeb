package org.example.echo.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreferenceMigrationTest {
    @TempDir Path directory;

    @Test
    void upgradePreservesExistingDataAndPreferencesSurviveDatabaseReopenWithConstraintsEnforced() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("preferences").toAbsolutePath().toString().replace('\\', '/')
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("13").load().migrate();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID media = UUID.randomUUID();
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            for (UUID user : new UUID[]{owner, other}) {
                execute(db, """
                    insert into users (user_id, username, username_normalized, password_hash, role,
                        is_active, email_verified, created_at, updated_at)
                    values (?, ?, ?, 'hash', 'USER', true, true, current_timestamp, current_timestamp)
                    """, user, user.toString(), user.toString());
            }
            execute(db, """
                insert into media (media_id, media_type, tmdb_id, sync_status, title,
                    last_synced_at, created_at, updated_at)
                values (?, 'MOVIE', 42, 'SYNCED', 'Existing movie',
                    current_timestamp, current_timestamp, current_timestamp)
                """, media);
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            insert(db, owner, "MOVIE", 42, media, null);
            insert(db, other, "MOVIE", 42, media, null);
            insert(db, owner, "ACTOR", 11, null, "Existing actor");
            insert(db, owner, "DIRECTOR", 11, null, "Existing actor");
            assertThat(assertThrows(SQLException.class, () -> insert(db, owner, "MOVIE", 42, media, null))
                .getSQLState()).isEqualTo("23505");
            assertThat(assertThrows(SQLException.class, () -> insert(db, owner, "ACTOR", 11, null, "Duplicate"))
                .getSQLState()).isEqualTo("23505");
            assertThat(assertThrows(SQLException.class, () -> insert(db, owner, "MOVIE", 99, UUID.randomUUID(), null))
                .getSQLState()).startsWith("23");
            assertThat(assertThrows(SQLException.class, () -> insert(db, UUID.randomUUID(), "MOVIE", 42, media, null))
                .getSQLState()).startsWith("23");
            for (String invalidType : new String[]{"MOVIE", "UNKNOWN"}) {
                assertThat(assertThrows(SQLException.class, () -> insert(db, owner, invalidType, 77, null, null))
                    .getSQLState()).startsWith("23");
            }
        }
        // All connections have closed: the next connection reopens the persisted file database.
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            assertThat(count(db, "select count(*) from users")).isEqualTo(2);
            assertThat(count(db, "select count(*) from media where title = 'Existing movie'")).isEqualTo(1);
            assertThat(count(db, "select count(*) from user_preferences")).isEqualTo(4);
            execute(db, "delete from users where user_id = ?", owner);
            assertThat(count(db, "select count(*) from user_preferences")).isEqualTo(1);
            assertThat(count(db, "select count(*) from media")).isEqualTo(1);
        }
    }

    private void insert(Connection db, UUID user, String type, long target, UUID media, String name) throws SQLException {
        execute(db, """
            insert into user_preferences (preference_id, user_id, type, tmdb_id, media_id, person_name, created_at)
            values (?, ?, ?, ?, ?, ?, current_timestamp)
            """, UUID.randomUUID(), user, type, target, media, name);
    }

    private void execute(Connection db, String sql, Object... arguments) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) {
                statement.setObject(i + 1, arguments[i]);
            }
            statement.executeUpdate();
        }
    }

    private long count(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
