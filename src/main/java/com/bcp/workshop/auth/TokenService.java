package com.bcp.workshop.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@ApplicationScoped
public class TokenService {
    public static final int EXPIRES_IN_SECONDS = 3600;

    private final byte[] signingKey;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.token.secret") String secret) {
        byte[] configuredKey = secret.getBytes(StandardCharsets.UTF_8);
        if (configuredKey.length < 32) {
            throw new IllegalStateException("auth.token.secret must contain at least 32 bytes");
        }
        signingKey = configuredKey.clone();
    }

    public String create(String username, String role, String client) {
        Instant issuedAt = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(username)
                .claim("role", role)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(EXPIRES_IN_SECONDS)));
        if (client != null) {
            claims.claim("client", client);
        }

        SignedJWT token = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claims.build()
        );
        try {
            token.sign(new MACSigner(signingKey));
            return token.serialize();
        } catch (JOSEException error) {
            throw new IllegalStateException("Unable to sign login token", error);
        }
    }
}
