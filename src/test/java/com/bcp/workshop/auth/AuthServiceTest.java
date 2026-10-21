package com.bcp.workshop.auth;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private static final String TEST_SECRET = "test-only-hs256-secret-32-bytes-long";
    private static final String PASSWORD = "Workshop123!";

    private final TokenService tokenService = new TokenService(TEST_SECRET);

    @Test
    void rejectsNullRequest() {
        AuthService service = new AuthService(new UserRepository("jdbc:sqlite::memory:"), tokenService);

        AuthService.LoginResult result = service.login(null);

        assertEquals(AuthService.Failure.REQUEST_NULL, result.failure());
    }

    @Test
    void rejectsMissingUsername() {
        AuthService service = new AuthService(new UserRepository("jdbc:sqlite::memory:"), tokenService);
        LoginRequest request = new LoginRequest();
        request.password = "secret";

        AuthService.LoginResult result = service.login(request);

        assertEquals(AuthService.Failure.USERNAME_REQUIRED, result.failure());
    }

    @Test
    void rejectsBlankUsername() {
        AuthService service = new AuthService(new UserRepository("jdbc:sqlite::memory:"), tokenService);
        LoginRequest request = new LoginRequest();
        request.username = "  ";
        request.password = "secret";

        AuthService.LoginResult result = service.login(request);

        assertEquals(AuthService.Failure.USERNAME_REQUIRED, result.failure());
    }

    @Test
    void rejectsMissingPassword() {
        AuthService service = new AuthService(new UserRepository("jdbc:sqlite::memory:"), tokenService);
        LoginRequest request = new LoginRequest();
        request.username = "student";

        AuthService.LoginResult result = service.login(request);

        assertEquals(AuthService.Failure.PASSWORD_REQUIRED, result.failure());
    }

    @Test
    void rejectsBlankPassword() {
        AuthService service = new AuthService(new UserRepository("jdbc:sqlite::memory:"), tokenService);
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "  ";

        AuthService.LoginResult result = service.login(request);

        assertEquals(AuthService.Failure.PASSWORD_REQUIRED, result.failure());
    }

    @Test
    void rejectsInvalidCredentials() {
        UserRepository repository = new UserRepository("jdbc:sqlite::memory:") {
            @Override
            public StoredUser findByUsername(String username) {
                return null;
            }
        };
        AuthService service = new AuthService(repository, tokenService);
        LoginRequest request = request("student", "wrong", null);

        AuthService.LoginResult result = service.login(request);

        assertEquals(AuthService.Failure.INVALID_CREDENTIALS, result.failure());
        assertEquals("student", result.username());
        assertNull(result.user());
        assertNull(result.token());
    }

    @Test
    void authenticatesUserAndIssuesSignedToken() throws Exception {
        UserRepository repository = new UserRepository("jdbc:sqlite::memory:") {
            @Override
            public StoredUser findByUsername(String username) {
                return new StoredUser(
                        new UserRecord(1, username, "Workshop Student", "STUDENT"),
                        PasswordHasher.hash("Workshop123!")
                );
            }
        };
        AuthService service = new AuthService(repository, tokenService);
        LoginRequest request = request("student", "Workshop123!", "angular-workshop");

        AuthService.LoginResult result = service.login(request);

        assertEquals(AuthService.Failure.NONE, result.failure());
        assertNotNull(result.user());
        assertEquals("student", result.user().username());
        assertTrue(SignedJWT.parse(result.token()).verify(
                new MACVerifier(TEST_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        ));
    }

    @Test
    void rejectsPasswordThatDoesNotMatchStoredHash() {
        UserRepository repository = new UserRepository("jdbc:sqlite::memory:") {
            @Override
            public StoredUser findByUsername(String username) {
                return new StoredUser(
                        new UserRecord(1, username, "Workshop Student", "STUDENT"),
                        PasswordHasher.hash("Workshop123!")
                );
            }
        };
        AuthService service = new AuthService(repository, tokenService);

        AuthService.LoginResult result = service.login(request("student", "wrong", null));

        assertEquals(AuthService.Failure.INVALID_CREDENTIALS, result.failure());
    }

    private LoginRequest request(String username, String password, String client) {
        LoginRequest request = new LoginRequest();
        request.username = username;
        request.password = password;
        request.client = client;
        return request;
    }

    @Test
    void usesDefaultClientWhenAbsentOrBlank() throws Exception {
        AuthService service = serviceWith(storedStudentRepository(), "angular-workshop", false);

        for (String client : new String[] {null, "   "}) {
            AuthService.LoginResult result = service.login(request("student", PASSWORD, client));

            assertEquals(AuthService.Failure.NONE, result.failure());
            assertEquals("angular-workshop", SignedJWT.parse(result.token()).getJWTClaimsSet().getStringClaim("client"));
        }
    }

    @Test
    void keepsSentClientValue() throws Exception {
        AuthService service = serviceWith(storedStudentRepository(), "angular-workshop", false);

        AuthService.LoginResult result = service.login(request("student", PASSWORD, "other-client"));

        assertEquals("other-client", SignedJWT.parse(result.token()).getJWTClaimsSet().getStringClaim("client"));
    }

    @Test
    void strictModeRejectsMissingOrBlankClientWithoutToken() {
        AuthService service = serviceWith(storedStudentRepository(), "angular-workshop", true);

        for (String client : new String[] {null, "   "}) {
            AuthService.LoginResult result = service.login(request("student", PASSWORD, client));

            assertEquals(AuthService.Failure.CLIENT_REQUIRED, result.failure());
            assertNull(result.token());
        }
    }

    @Test
    void strictModeAcceptsSentClient() {
        AuthService service = serviceWith(storedStudentRepository(), "angular-workshop", true);

        AuthService.LoginResult result = service.login(request("student", PASSWORD, "angular-workshop"));

        assertEquals(AuthService.Failure.NONE, result.failure());
        assertNotNull(result.token());
    }

    @Test
    void databaseFailureIsReportedAsInvalidCredentials() {
        AuthService service = new AuthService(new UserRepository("jdbc:invalid:unavailable"), tokenService);

        AuthService.LoginResult result = service.login(request("student", PASSWORD, "angular-workshop"));

        assertEquals(AuthService.Failure.INVALID_CREDENTIALS, result.failure());
        assertNull(result.token());
    }

    @Test
    void defaultClientWarningDoesNotLogTokenOrPassword() {
        AuthService service = serviceWith(storedStudentRepository(), "angular-workshop", false);
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Logger logger = Logger.getLogger(AuthService.class.getName());
        logger.addHandler(handler);
        try {
            AuthService.LoginResult result = service.login(request("student", PASSWORD, null));

            assertFalse(messages.isEmpty());
            for (String message : messages) {
                assertFalse(message.contains(PASSWORD));
                assertFalse(message.contains(result.token()));
            }
        } finally {
            logger.removeHandler(handler);
        }
    }

    private AuthService serviceWith(UserRepository repository, String defaultClient, boolean clientRequired) {
        return new AuthService(repository, tokenService, defaultClient, clientRequired);
    }

    private UserRepository storedStudentRepository() {
        return new UserRepository("jdbc:sqlite::memory:") {
            @Override
            public StoredUser findByUsername(String username) {
                return new StoredUser(
                        new UserRecord(1, username, "Workshop Student", "STUDENT"),
                        PasswordHasher.hash(PASSWORD)
                );
            }
        };
    }
}
