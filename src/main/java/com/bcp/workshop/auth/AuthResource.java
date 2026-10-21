package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String REQUEST_NULL_MESSAGE = "Request is null";
    private static final String USERNAME_REQUIRED_MESSAGE = "Username is required";
    private static final String PASSWORD_REQUIRED_MESSAGE = "Password is required";
    private static final String CLIENT_REQUIRED_MESSAGE = "Client is required";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid user %s or password";

    private final AuthService authService;

    @Inject
    public AuthResource(AuthService authService) {
        this.authService = authService;
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            return toResponse(authService.login(request));
        } catch (Exception error) {
            return internalServerError(error);
        }
    }

    private Response toResponse(AuthService.LoginResult result) {
        return switch (result.failure()) {
            case NONE -> Response.ok(LoginResponse.from(result)).build();
            case REQUEST_NULL -> error(Response.Status.BAD_REQUEST, REQUEST_NULL_MESSAGE);
            case USERNAME_REQUIRED -> error(Response.Status.BAD_REQUEST, USERNAME_REQUIRED_MESSAGE);
            case PASSWORD_REQUIRED -> error(Response.Status.BAD_REQUEST, PASSWORD_REQUIRED_MESSAGE);
            case CLIENT_REQUIRED -> error(Response.Status.BAD_REQUEST, CLIENT_REQUIRED_MESSAGE);
            case INVALID_CREDENTIALS -> error(
                    Response.Status.UNAUTHORIZED,
                    INVALID_CREDENTIALS_MESSAGE.formatted(result.username())
            );
        };
    }

    private Response error(Response.Status status, String message) {
        return Response.status(status).entity(Map.of("message", message)).build();
    }

    static Response internalServerError(Exception error) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of(
                "message", error.getMessage() == null ? "Unknown error" : error.getMessage(),
                "exception", error.getClass().getName()
        )).build();
    }
}
