package com.omragul.notification.util;

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
 * Helper utility for generating signed test JWTs using a dynamic in-memory RSA keypair.
 * Must not be used in production code.
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

    public static String generateUserToken(UUID userId) {
        return generateToken(userId, List.of("USER"), List.of());
    }

    public static String generateAdminToken(UUID userId) {
        return generateToken(userId, List.of("ADMIN"), List.of("NOTIFICATION_ADMIN"));
    }

    public static String generateInternalToken(UUID serviceId) {
        return generateToken(serviceId, List.of("INTERNAL"), List.of());
    }

    public static String generateToken(UUID userId, List<String> roles, List<String> permissions) {
        PrivateKey privateKey = KEY_PAIR.getPrivate();

        Instant now = Instant.now();
        Instant expiry = now.plus(15, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .claim("username", "testuser")
                .claim("email", "test@omragul.com")
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
