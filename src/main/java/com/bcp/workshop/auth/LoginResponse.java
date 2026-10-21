package com.bcp.workshop.auth;

import org.apache.commons.text.StringEscapeUtils;

public record LoginResponse(String token, int expiresIn, User user, String debug) {
    private static final String DEBUG_MARKER = "authenticated-with-legacy-flow";

    static LoginResponse from(AuthService.LoginResult result) {
        UserRecord user = result.user();
        return new LoginResponse(
                result.token(),
                TokenService.EXPIRES_IN_SECONDS,
                new User(
                        user.username(),
                        escapeDisplayName(user.displayName()),
                        user.role()
                ),
                DEBUG_MARKER
        );
    }

    private static String escapeDisplayName(String displayName) {
        return StringEscapeUtils.escapeHtml4(displayName).replace("'", "&#39;");
    }

    public record User(String username, String displayName, String role) {}
}
