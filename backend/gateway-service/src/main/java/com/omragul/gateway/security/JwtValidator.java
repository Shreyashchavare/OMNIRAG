package com.omragul.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Validates JWT access tokens issued by Identity Service using RS256 public key.
 */
@Component
public class JwtValidator {

    private final PublicKey publicKey;
    private final String issuer;

    public JwtValidator(
            @Value("${gateway.jwt.public-key:classpath:certs/public_key.pem}") Resource publicKeyResource,
            @Value("${gateway.jwt.issuer:http://localhost:8080}") String issuer
    ) {
        this.publicKey = loadPublicKey(publicKeyResource);
        this.issuer = issuer;
    }

    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null) {
            throw new IllegalArgumentException("JWT subject claim is missing");
        }
        return UUID.fromString(subject);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    public String extractUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public String extractEmail(Claims claims) {
        return claims.get("email", String.class);
    }

    private PublicKey loadPublicKey(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            String key = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            key = key
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decodedKey = Base64.getDecoder().decode(key);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA public key for JWT validation: " + resource.getDescription(), e);
        }
    }
}
