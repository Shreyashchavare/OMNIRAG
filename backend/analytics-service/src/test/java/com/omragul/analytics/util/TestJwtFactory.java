package com.omragul.analytics.util;

import io.jsonwebtoken.Jwts;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Helper for generating test JWTs signed with the test RSA private key.
 * Must not be used in production code.
 */
public class TestJwtFactory {

    private static final String ISSUER = "http://localhost:8080";

    public static String generateAdminToken(UUID userId) {
        return generateToken(userId, List.of("ADMIN"), List.of("ANALYTICS_READ"));
    }

    public static String generateUserToken(UUID userId) {
        return generateToken(userId, List.of("USER"), List.of());
    }

    public static String generateInternalToken(UUID serviceId) {
        return generateToken(serviceId, List.of("INTERNAL"), List.of());
    }

    private static String generateToken(UUID userId, List<String> roles, List<String> permissions) {
        PrivateKey privateKey = loadTestPrivateKey();

        Instant now = Instant.now();
        Instant expiry = now.plus(15, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private static PrivateKey loadTestPrivateKey() {
        try {
            ClassPathResource resource = new ClassPathResource("certs/test_private_key_pkcs8.pem");
            try (InputStream inputStream = resource.getInputStream()) {
                String key = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                key = key
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s+", "");

                byte[] decodedKey = Base64.getDecoder().decode(key);
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return keyFactory.generatePrivate(keySpec);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load test RSA private key", e);
        }
    }
}
