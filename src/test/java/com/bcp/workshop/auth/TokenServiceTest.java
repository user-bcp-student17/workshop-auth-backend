package com.bcp.workshop.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {
    private static final String TEST_SECRET = "test-only-hs256-secret-32-bytes-long";

    @Test
    void rejectsSigningSecretShorterThanHs256Minimum() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new TokenService("too-short")
        );

        assertEquals("auth.token.secret must contain at least 32 bytes", error.getMessage());
    }

    @Test
    void signsClaimsAndRejectsTamperedToken() throws Exception {
        TokenService service = new TokenService(TEST_SECRET);

        String token = service.create("student", "STUDENT", "angular-workshop");
        SignedJWT signedToken = SignedJWT.parse(token);
        var verifier = new MACVerifier(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = signedToken.getJWTClaimsSet();

        assertEquals(JWSAlgorithm.HS256, signedToken.getHeader().getAlgorithm());
        assertTrue(signedToken.verify(verifier));
        assertEquals("student", claims.getSubject());
        assertEquals("STUDENT", claims.getStringClaim("role"));
        assertEquals("angular-workshop", claims.getStringClaim("client"));
        assertEquals(
                TokenService.EXPIRES_IN_SECONDS,
                (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000
        );

        String[] segments = token.split("\\.", -1);
        String changedPayload = (segments[1].charAt(0) == 'A' ? "B" : "A") + segments[1].substring(1);
        SignedJWT tamperedToken = SignedJWT.parse(segments[0] + "." + changedPayload + "." + segments[2]);

        assertFalse(tamperedToken.verify(verifier));
    }

    @Test
    void omitsOptionalClientClaimWhenAbsent() throws Exception {
        SignedJWT token = SignedJWT.parse(new TokenService(TEST_SECRET).create("student", "STUDENT", null));

        assertFalse(token.getJWTClaimsSet().getClaims().containsKey("client"));
    }
}
