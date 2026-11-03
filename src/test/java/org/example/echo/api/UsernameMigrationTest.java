package org.example.echo.api;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsernameMigrationTest {
    @Test
    void v11PreservesExistingAccountsAndBackfillsCanonicalLogin() throws Exception {
        String url = databaseAtV10();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            insert(connection, "Existing_User");
            insert(connection, "Кириллица");
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var result = connection.createStatement().executeQuery("select username, username_normalized, password_hash from users")) {
            int count = 0;
            while (result.next()) {
                count++;
                assertThat(result.getString(2)).isEqualTo(result.getString(1).toLowerCase(java.util.Locale.ROOT));
                assertThat(result.getString(3)).isEqualTo("existing-hash");
            }
            assertThat(count).isEqualTo(2);
        }
    }

    @Test
    void migrationStopsOnAmbiguousExistingAccountsWithoutDeletingThem() throws Exception {
        String url = databaseAtV10();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            insert(connection, "SameName"); insert(connection, "samename");
        }
        assertThatThrownBy(() -> Flyway.configure().dataSource(url, "sa", "").load().migrate())
            .isInstanceOf(FlywayException.class);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var result = connection.createStatement().executeQuery("select count(*) from users")) {
            result.next(); assertThat(result.getInt(1)).isEqualTo(2);
        }
    }

    private static String databaseAtV10() {
        String url = "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("10").load().migrate();
        return url;
    }
    private static void insert(java.sql.Connection connection, String name) throws Exception {
        try (var statement = connection.prepareStatement("insert into users (user_id,username,password_hash,role,is_active,created_at,updated_at) values (?,?,'existing-hash','USER',true,current_timestamp,current_timestamp)")) {
            statement.setObject(1, UUID.randomUUID()); statement.setString(2, name); statement.executeUpdate();
        }
    }
}
