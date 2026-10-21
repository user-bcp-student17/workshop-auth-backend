package com.bcp.workshop.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

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
    void alwaysIncludesClientClaim() throws Exception {
        SignedJWT token = SignedJWT.parse(new TokenService(TEST_SECRET).create("student", "STUDENT", "angular-workshop"));

        assertEquals("angular-workshop", token.getJWTClaimsSet().getStringClaim("client"));
    }

    @Test
    void rejectsMissingClientClaim() {
        TokenService service = new TokenService(TEST_SECRET);

        assertThrows(NullPointerException.class, () -> service.create("student", "STUDENT", null));
    }

    @Test
    void expiresInMatchesExpMinusNowWithinFiveSeconds() throws Exception {
        SignedJWT token = SignedJWT.parse(new TokenService(TEST_SECRET).create("student", "STUDENT", "angular-workshop"));
        long remainingSeconds = (token.getJWTClaimsSet().getExpirationTime().getTime() - System.currentTimeMillis()) / 1000;

        assertTrue(Math.abs(remainingSeconds - TokenService.EXPIRES_IN_SECONDS) <= 5);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        SignedJWT expired = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject("student")
                        .claim("client", "angular-workshop")
                        .issueTime(Date.from(past.minusSeconds(3600)))
                        .expirationTime(Date.from(past))
                        .build()
        );
        expired.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
        SignedJWT parsed = SignedJWT.parse(expired.serialize());

        assertTrue(parsed.verify(new MACVerifier(TEST_SECRET.getBytes(StandardCharsets.UTF_8))));
        assertThrows(BadJWTException.class, () -> new DefaultJWTClaimsVerifier<SecurityContext>(
                (JWTClaimsSet) null, (java.util.Set<String>) null
        ).verify(parsed.getJWTClaimsSet(), null));
    }
}
