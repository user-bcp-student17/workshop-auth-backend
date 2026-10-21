package com.bcp.workshop.auth;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthResourceUnitTest {
    private static final String TEST_SECRET = "test-only-hs256-secret-32-bytes-long";

    @Test
    void mapsNullRequestToContractualBadRequest() {
        Response response = resourceWith(
                AuthService.LoginResult.failure(AuthService.Failure.REQUEST_NULL, null)
        ).login(null);

        assertMessage(response, Response.Status.BAD_REQUEST, "Request is null");
    }

    @Test
    void mapsMissingUsernameToContractualBadRequest() {
        Response response = resourceWith(
                AuthService.LoginResult.failure(AuthService.Failure.USERNAME_REQUIRED, null)
        ).login(new LoginRequest());

        assertMessage(response, Response.Status.BAD_REQUEST, "Username is required");
    }

    @Test
    void mapsMissingPasswordToContractualBadRequest() {
        Response response = resourceWith(
                AuthService.LoginResult.failure(AuthService.Failure.PASSWORD_REQUIRED, null)
        ).login(new LoginRequest());

        assertMessage(response, Response.Status.BAD_REQUEST, "Password is required");
    }

    @Test
    void mapsMissingClientToContractualBadRequestInStrictMode() {
        Response response = resourceWith(
                AuthService.LoginResult.failure(AuthService.Failure.CLIENT_REQUIRED, null)
        ).login(new LoginRequest());

        assertMessage(response, Response.Status.BAD_REQUEST, "Client is required");
    }

    @Test
    void mapsInvalidCredentialsToContractualUnauthorizedResponse() {
        Response response = resourceWith(
                AuthService.LoginResult.failure(AuthService.Failure.INVALID_CREDENTIALS, "student")
        ).login(new LoginRequest());

        assertMessage(response, Response.Status.UNAUTHORIZED, "Invalid user student or password");
    }

    @Test
    void mapsSuccessfulAuthenticationToResponseDto() {
        UserRecord user = new UserRecord(1, "student", "Workshop Student", "STUDENT");
        AuthService.LoginResult result = AuthService.LoginResult.authenticated(user, "signed-token");

        Response response = resourceWith(result).login(new LoginRequest());

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getEntity());
        assertEquals("signed-token", body.token());
        assertEquals(TokenService.EXPIRES_IN_SECONDS, body.expiresIn());
        assertEquals("student", body.user().username());
        assertEquals("Workshop Student", body.user().displayName());
        assertEquals("STUDENT", body.user().role());
        assertEquals("authenticated-with-legacy-flow", body.debug());
    }

    @Test
    void escapesDisplayNameWhenCreatingResponseDto() {
        UserRecord user = new UserRecord(1, "student", "O'Brien & Co", "STUDENT");
        AuthService.LoginResult result = AuthService.LoginResult.authenticated(user, "signed-token");

        Response response = resourceWith(result).login(new LoginRequest());

        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getEntity());
        assertEquals("O&#39;Brien &amp; Co", body.user().displayName());
    }

    @Test
    void preservesInternalErrorResponseShape() {
        Response response = AuthResource.internalServerError(new IllegalStateException("database failure"));

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("database failure", body.get("message"));
        assertEquals(IllegalStateException.class.getName(), body.get("exception"));
    }

    @Test
    void usesGenericMessageWhenExceptionHasNoMessage() {
        Response response = AuthResource.internalServerError(new IllegalStateException());

        Map<?, ?> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("Unknown error", body.get("message"));
    }

    private AuthResource resourceWith(AuthService.LoginResult result) {
        AuthService service = new AuthService(
                new UserRepository("jdbc:sqlite::memory:"),
                new TokenService(TEST_SECRET)
        ) {
            @Override
            public LoginResult login(LoginRequest request) {
                return result;
            }
        };
        return new AuthResource(service);
    }

    private void assertMessage(Response response, Response.Status status, String message) {
        assertEquals(status.getStatusCode(), response.getStatus());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals(message, body.get("message"));
    }
}
