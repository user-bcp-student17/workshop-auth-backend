package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesLegacyPlaintextPasswordsAndSeedsHashedDefaults() throws Exception {
        Class.forName("org.sqlite.JDBC");
        String databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("legacy-users.db").toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE legacy_users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT UNIQUE,
                        password TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        role TEXT NOT NULL
                    )
                    """);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO legacy_users(username, password, display_name, role)
                    VALUES (?, ?, ?, ?)
                    """)) {
                insert.setString(1, "legacy");
                insert.setString(2, "LegacyPassword123!");
                insert.setString(3, "Legacy User");
                insert.setString(4, "STUDENT");
                insert.executeUpdate();
            }
        }

        UserRepository repository = new UserRepository(databaseUrl);
        repository.initialize();

        UserRepository.StoredUser migratedUser = repository.findByUsername("legacy");
        assertNotNull(migratedUser);
        assertTrue(PasswordHasher.verify("LegacyPassword123!", migratedUser.passwordHash()));

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement query = connection.prepareStatement(
                     "SELECT password, password_hash FROM legacy_users WHERE username = ?"
             )) {
            query.setString(1, "legacy");
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals("", result.getString("password"));
                assertNotEquals("LegacyPassword123!", result.getString("password_hash"));
            }
        }

        UserRepository.StoredUser seededUser = repository.findByUsername("student");
        assertNotNull(seededUser);
        assertTrue(PasswordHasher.verify("Workshop123!", seededUser.passwordHash()));
    }

    @Test
    void failsInitializationWhenDatabaseCannotBeOpened() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new UserRepository("invalid-database-url").initialize()
        );

        assertEquals("Unable to initialize authentication database", error.getMessage());
    }
}
