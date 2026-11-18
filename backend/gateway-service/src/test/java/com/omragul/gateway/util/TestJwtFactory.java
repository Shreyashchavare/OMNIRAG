package com.omragul.gateway.util;

import io.jsonwebtoken.Jwts;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Helper for generating test JWTs signed with a dynamic in-memory RSA keypair.
 * Strictly used in test suite only.
 */
public class TestJwtFactory {

    private static final String ISSUER = "http://localhost:8080";
    private static final KeyPair KEY_PAIR;

    static {
        try {
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
            keyPairGen.initialize(2048);
            KEY_PAIR = keyPairGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize test RSA key pair", e);
        }
    }

    public static PublicKey getPublicKey() {
        return KEY_PAIR.getPublic();
    }

    public static PrivateKey getPrivateKey() {
        return KEY_PAIR.getPrivate();
    }

    public static Resource getPublicKeyResource() {
        String base64 = Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
        return new ByteArrayResource(base64.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateValidToken(UUID userId, List<String> roles) {
        return generateToken(userId, "testuser", "test@example.com", roles, ISSUER, 15);
    }

    public static String generateExpiredToken(UUID userId) {
        return generateToken(userId, "testuser", "test@example.com", List.of("ROLE_USER"), ISSUER, -15);
    }

    public static String generateInvalidIssuerToken(UUID userId) {
        return generateToken(userId, "testuser", "test@example.com", List.of("ROLE_USER"), "http://wrong-issuer:8080", 15);
    }

    public static String generateToken(
            UUID userId,
            String username,
            String email,
            List<String> roles,
            String issuer,
            long expiryMinutes
    ) {
        PrivateKey privateKey = KEY_PAIR.getPrivate();
        Instant now = Instant.now();
        Instant expiry = now.plus(expiryMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim("username", username)
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
