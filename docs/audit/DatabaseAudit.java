import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;

/** Standalone audit probe, not production code. Uses only a newly created target directory. */
public class DatabaseAudit {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "audit-db-").toAbsolutePath();
        String url = "jdbc:h2:file:" + directory.resolve("echo").toString().replace('\\', '/')
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        String location = "filesystem:src/main/resources/db/migration";
        Flyway.configure().dataSource(url, "sa", "audit").locations(location).target("9").load().migrate();
        UUID originalId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, "sa", "audit")) {
            insertUser(connection, originalId, "original@example.invalid", "AuditCase");
        }
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "audit").locations(location).load();
        flyway.migrate();
        flyway.validate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "audit")) {
            check("V9 populated database upgraded to V10", count(connection,
                "select count(*) from users where user_id='" + originalId + "' and email='original@example.invalid'") == 1);
            check("all 10 migrations recorded", count(connection,
                "select count(*) from flyway_schema_history where success=true and version is not null") == 10);
            insertUser(connection, UUID.randomUUID(), null, "NoEmailOne");
            insertUser(connection, UUID.randomUUID(), null, "NoEmailTwo");
            check("multiple users with NULL email", count(connection, "select count(*) from users where email is null") == 2);
            try {
                insertUser(connection, UUID.randomUUID(), "another@example.invalid", "AuditCase");
                throw new AssertionError("Exact duplicate username was accepted");
            } catch (SQLException expected) {
                check("exact duplicate username rejected", "23505".equals(expected.getSQLState()));
            }
            insertUser(connection, UUID.randomUUID(), "casevariant@example.invalid", "auditcase");
            check("schema permits ambiguous case-insensitive usernames (risk reproduced)",
                count(connection, "select count(*) from users where upper(username)=upper('AuditCase')") == 2);
            UUID reviewId = UUID.randomUUID();
            UUID answerId = UUID.randomUUID();
            connection.createStatement().executeUpdate("insert into reviews (review_id,user_id,tmdb_movie_id,movie_title,rating,review_text,created_at,updated_at) values ('"
                + reviewId + "','" + originalId + "',603,'Audit movie',8,'Audit review',current_timestamp,current_timestamp)");
            connection.createStatement().executeUpdate("insert into answers (answer_id,review_id,user_id,answer_text,created_at,updated_at) values ('"
                + answerId + "','" + reviewId + "','" + originalId + "','Audit answer',current_timestamp,current_timestamp)");
            connection.createStatement().executeUpdate("delete from users where user_id='" + originalId + "'");
            check("user deletion cascades to reviews and answers", count(connection, "select count(*) from reviews") == 0
                && count(connection, "select count(*) from answers") == 0);
        }
        // All previous JDBC connections and Flyway pools have closed; reopen the file DB.
        try (Connection reopened = DriverManager.getConnection(url, "sa", "audit")) {
            check("data persists after closing and reopening file database", count(reopened, "select count(*) from users") == 3);
        }
        try {
            var type = DatabaseTypeRegister.getDatabaseTypeForUrl("jdbc:postgresql://localhost:5432/audit", Flyway.configure());
            System.out.println("PostgreSQL Flyway handler: " + type.getName());
        } catch (Exception error) {
            System.out.println("CONFIRMED missing PostgreSQL Flyway handler: " + error.getMessage());
        }
        System.out.println("Isolated audit database: " + directory);
    }
    private static void insertUser(Connection connection, UUID id, String email, String username) throws Exception {
        try (var statement = connection.prepareStatement("insert into users (user_id,email,username,password_hash,role,is_active,created_at,updated_at) values (?,?,?,'audit-not-a-real-hash','USER',true,current_timestamp,current_timestamp)")) {
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.setString(3, username);
            statement.executeUpdate();
        }
    }
    private static long count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next(); return result.getLong(1);
        }
    }
    private static void check(String name, boolean success) {
        if (!success) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }
}
