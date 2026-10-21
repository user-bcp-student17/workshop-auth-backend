package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Logger;

@ApplicationScoped
public class AuthService {
    static final String DEFAULT_CLIENT = "angular-workshop";
    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final String defaultClient;
    private final boolean clientRequired;

    @Inject
    public AuthService(
            UserRepository userRepository,
            TokenService tokenService,
            @ConfigProperty(name = "auth.client.default", defaultValue = DEFAULT_CLIENT) String defaultClient,
            @ConfigProperty(name = "auth.client.required", defaultValue = "false") boolean clientRequired
    ) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.defaultClient = defaultClient;
        this.clientRequired = clientRequired;
    }

    AuthService(UserRepository userRepository, TokenService tokenService) {
        this(userRepository, tokenService, DEFAULT_CLIENT, false);
    }

    public LoginResult login(LoginRequest request) {
        Failure validationFailure = validate(request);
        if (validationFailure != Failure.NONE) {
            return LoginResult.failure(validationFailure, null);
        }

        UserRepository.StoredUser storedUser = userRepository.findByUsername(request.username);
        if (storedUser == null || !PasswordHasher.verify(request.password, storedUser.passwordHash())) {
            return LoginResult.failure(Failure.INVALID_CREDENTIALS, request.username);
        }

        UserRecord user = storedUser.user();
        String token = tokenService.create(user.username(), user.role(), resolveClient(request.client));
        return LoginResult.authenticated(user, token);
    }

    private String resolveClient(String client) {
        if (!isBlank(client)) {
            return client;
        }
        LOG.warning("Login without client; using auth.client.default");
        return defaultClient;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Failure validate(LoginRequest request) {
        if (request == null) {
            return Failure.REQUEST_NULL;
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return Failure.USERNAME_REQUIRED;
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return Failure.PASSWORD_REQUIRED;
        }
        if (clientRequired && isBlank(request.client)) {
            return Failure.CLIENT_REQUIRED;
        }
        return Failure.NONE;
    }

    public enum Failure {
        NONE,
        REQUEST_NULL,
        USERNAME_REQUIRED,
        PASSWORD_REQUIRED,
        CLIENT_REQUIRED,
        INVALID_CREDENTIALS
    }

    public record LoginResult(Failure failure, String username, UserRecord user, String token) {
        static LoginResult failure(Failure failure, String username) {
            return new LoginResult(failure, username, null, null);
        }

        static LoginResult authenticated(UserRecord user, String token) {
            return new LoginResult(Failure.NONE, null, user, token);
        }
    }
}
