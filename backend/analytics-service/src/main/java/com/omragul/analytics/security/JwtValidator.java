package com.omragul.analytics.security;

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
import java.util.UUID;

/**
 * JWT validation service for the Analytics Service.
 * Uses the Identity Service's RSA public key to verify RS256 tokens.
 * This service does NOT issue tokens; it only validates and reads them.
 */
@Component
public class JwtValidator {

    private final PublicKey publicKey;
    private final String issuer;

    public JwtValidator(
            @Value("${analytics.jwt.public-key}") Resource publicKeyResource,
            @Value("${analytics.jwt.issuer}") String issuer
    ) {
        this.publicKey = loadPublicKey(publicKeyResource);
        this.issuer = issuer;
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        String subject = extractClaims(token).getSubject();
        return UUID.fromString(subject);
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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
            throw new IllegalStateException("Failed to load RSA public key for JWT validation", e);
        }
    }
}
