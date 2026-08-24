package com.omragul.identity.service.security;

import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.entity.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.security.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtServiceImpl implements JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long expirationMinutes;
    private final String issuer;

    public JwtServiceImpl(
            @Value("${identity.jwt.private-key}") Resource privateKeyResource,
            @Value("${identity.jwt.public-key}") Resource publicKeyResource,
            @Value("${identity.jwt.expiration-minutes}") long expirationMinutes,
            @Value("${identity.jwt.issuer}") String issuer
    ) {
        this.privateKey = loadPrivateKey(privateKeyResource);
        this.publicKey = loadPublicKey(publicKeyResource);
        this.expirationMinutes = expirationMinutes;
        this.issuer = issuer;
    }

    @Override
    public String generateAccessToken(User user) {

        Instant now = Instant.now();

        Instant expiration = now.plus(
                expirationMinutes,
                ChronoUnit.MINUTES
        );

        List<String> roles = user.getUserRoles()
                .stream()
                .map(UserRole::getRole)
                .map(role -> role.getRoleName().name())
                .toList();

        return Jwts.builder()
                .subject(user.getUserId().toString())
                .issuer(issuer)
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Override
    public Claims extractClaims(String token) {

        Jws<Claims> claimsJws = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);

        return claimsJws.getPayload();
    }

    @Override
    public UUID extractUserId(String token) {

        String subject = extractClaims(token)
                .getSubject();

        return UUID.fromString(subject);
    }

    @Override
    public boolean isTokenValid(String token) {

        try {
            extractClaims(token);
            return true;

        } catch (Exception exception) {
            return false;
        }
    }

    private PrivateKey loadPrivateKey(Resource resource) {

        try (InputStream inputStream = resource.getInputStream()) {

            String key = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            key = key
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decodedKey = Base64.getDecoder()
                    .decode(key);

            PKCS8EncodedKeySpec keySpec =
                    new PKCS8EncodedKeySpec(decodedKey);

            KeyFactory keyFactory =
                    KeyFactory.getInstance("RSA");

            return keyFactory.generatePrivate(keySpec);

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to load RSA private key",
                    exception
            );
        }
    }

    private PublicKey loadPublicKey(Resource resource) {

        try (InputStream inputStream = resource.getInputStream()) {

            String key = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            key = key
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decodedKey = Base64.getDecoder()
                    .decode(key);

            X509EncodedKeySpec keySpec =
                    new X509EncodedKeySpec(decodedKey);

            KeyFactory keyFactory =
                    KeyFactory.getInstance("RSA");

            return keyFactory.generatePublic(keySpec);

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to load RSA public key",
                    exception
            );
        }
    }
}