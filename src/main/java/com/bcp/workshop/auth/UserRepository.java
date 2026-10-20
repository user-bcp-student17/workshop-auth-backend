package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class UserRepository {
    private static final String DEFAULT_DATABASE_URL = "jdbc:sqlite:./data/workshop.db";
    private static final String FIND_BY_USERNAME_SQL =
            "SELECT id, username, display_name, role, password_hash FROM legacy_users WHERE username = ?";

    @Inject
    @ConfigProperty(name = "auth.db.url", defaultValue = DEFAULT_DATABASE_URL)
    String databaseUrl;

    public UserRepository() {}

    UserRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to prepare authentication database directory", error);
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            connection.setAutoCommit(false);
            try {
                createSchema(connection);
                migratePlaintextPasswords(connection);
                insertDefaultUsers(connection);
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw new IllegalStateException("Unable to initialize authentication database", error);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to initialize authentication database", error);
        }
    }

    public StoredUser findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME_SQL)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new StoredUser(toUserRecord(result), result.getString("password_hash"));
            }
        } catch (SQLException error) {
            error.printStackTrace();
        }
        return null;
    }

    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS legacy_users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT UNIQUE,
                        password TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        role TEXT NOT NULL,
                        password_hash TEXT
                    )
                    """);
        }
        ensurePasswordHashColumn(connection);
    }

    private void ensurePasswordHashColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (columns.next()) {
                if ("password_hash".equals(columns.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users ADD COLUMN password_hash TEXT");
        }
    }

    private void migratePlaintextPasswords(Connection connection) throws SQLException {
        List<LegacyPassword> legacyPasswords = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, password FROM legacy_users WHERE password_hash IS NULL OR password_hash = ''"
             )) {
            while (rows.next()) {
                legacyPasswords.add(new LegacyPassword(rows.getLong("id"), rows.getString("password")));
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE legacy_users SET password_hash = ?, password = '' WHERE id = ?"
        )) {
            for (LegacyPassword legacyPassword : legacyPasswords) {
                statement.setString(1, PasswordHasher.hash(legacyPassword.password()));
                statement.setLong(2, legacyPassword.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE legacy_users SET password = '' WHERE password IS NOT NULL");
        }
    }

    private void insertDefaultUsers(Connection connection) throws SQLException {
        insertDefaultUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
        insertDefaultUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    private void insertDefaultUser(
            Connection connection,
            String username,
            String password,
            String displayName,
            String role
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO legacy_users(username, password, password_hash, display_name, role)
                VALUES (?, '', ?, ?, ?)
                """)) {
            statement.setString(1, username);
            statement.setString(2, PasswordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private void rollback(Connection connection, Exception error) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            error.addSuppressed(rollbackError);
        }
    }

    private UserRecord toUserRecord(ResultSet result) throws SQLException {
        return new UserRecord(
                result.getLong("id"),
                result.getString("username"),
                result.getString("display_name"),
                result.getString("role")
        );
    }

    record StoredUser(UserRecord user, String passwordHash) {}

    private record LegacyPassword(long id, String password) {}
}
