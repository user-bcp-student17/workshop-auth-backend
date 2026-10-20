package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    private final UserRepository userRepository;
    private final TokenService tokenService;

    @Inject
    public AuthService(UserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
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
        String token = tokenService.create(user.username(), user.role(), request.client);
        return LoginResult.authenticated(user, token);
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
        return Failure.NONE;
    }

    public enum Failure {
        NONE,
        REQUEST_NULL,
        USERNAME_REQUIRED,
        PASSWORD_REQUIRED,
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
