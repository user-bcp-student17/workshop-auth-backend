package com.bcp.workshop.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final byte[] TEST_SIGNING_SECRET =
            "test-only-hs256-secret-32-bytes-long".getBytes(StandardCharsets.UTF_8);
    private static final long EXPECTED_TOKEN_TTL_SECONDS = 3600L;

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("token", notNullValue());
    }

    @Test
    void rejectsNullRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsSqlInjectionCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"' OR '1'='1' --\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void returnsSignedTokenWithExplicitClaims() throws Exception {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().path("token");

        SignedJWT signedToken = SignedJWT.parse(token);
        assertEquals(JWSAlgorithm.HS256, signedToken.getHeader().getAlgorithm());
        assertTrue(signedToken.verify(new MACVerifier(TEST_SIGNING_SECRET)));

        var claims = signedToken.getJWTClaimsSet();
        assertEquals("student", claims.getSubject());
        assertEquals("STUDENT", claims.getStringClaim("role"));
        assertEquals("angular-workshop", claims.getStringClaim("client"));
        assertEquals(
                EXPECTED_TOKEN_TTL_SECONDS,
                (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000
        );

        String[] segments = token.split("\\.", -1);
        String changedPayload = (segments[1].charAt(0) == 'A' ? "B" : "A") + segments[1].substring(1);
        assertFalse(SignedJWT.parse(segments[0] + "." + changedPayload + "." + segments[2])
                .verify(new MACVerifier(TEST_SIGNING_SECRET)));
    }

    @Test
    void keepsClientOptionalAndOmitsItsClaimWhenAbsent() throws Exception {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().path("token");

        assertFalse(SignedJWT.parse(token).getJWTClaimsSet().getClaims().containsKey("client"));
    }
}
