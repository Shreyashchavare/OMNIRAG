package com.omragul.gateway.security;

import com.omragul.gateway.config.GatewayProperties;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Generates short-lived, tamper-resistant RS256 JWT assertion tokens
 * for secure downstream microservice propagation of authenticated user identity.
 */
@Slf4j
@Service
public class GatewayAssertionService {

    public static final String ISSUER = "gateway-service";
    public static final long VALIDITY_SECONDS = 60L;
    public static final java.util.Set<String> VALID_HTTP_METHODS = java.util.Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
    );

    private final GatewayProperties gatewayProperties;
    private final boolean enabled;
    private final PrivateKey privateKey;

    @Autowired
    public GatewayAssertionService(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
        this.enabled = (gatewayProperties != null && gatewayProperties.getAssertion() != null)
                && gatewayProperties.getAssertion().isEnabled();
        if (this.enabled) {
            String keyPath = (gatewayProperties.getAssertion() != null)
                    ? gatewayProperties.getAssertion().getPrivateKeyPath()
                    : null;
            this.privateKey = resolveAndLoadPrivateKey(keyPath);
        } else {
            log.warn("Gateway assertion signing is disabled by configuration");
            this.privateKey = null;
        }
    }

    /**
     * Programmatic/test constructor with explicit PrivateKey.
     */
    public GatewayAssertionService(PrivateKey privateKey) {
        this.gatewayProperties = null;
        this.enabled = (privateKey != null);
        this.privateKey = privateKey;
    }

    /**
     * Programmatic constructor with properties and explicit PrivateKey.
     */
    public GatewayAssertionService(GatewayProperties gatewayProperties, PrivateKey privateKey) {
        this.gatewayProperties = gatewayProperties;
        this.enabled = (privateKey != null);
        this.privateKey = privateKey;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public PrivateKey getPrivateKey() {
        return this.privateKey;
    }

    /**
     * Creates an RS256-signed Gateway assertion JWT with default method GET and root path /.
     */
    public String createAssertion(String userId, String roles, String requestId, String targetAudience) {
        return createAssertion(userId, roles, requestId, targetAudience, "GET", "/");
    }

    /**
     * Creates an RS256-signed Gateway assertion JWT with cryptographically bound HTTP method and path.
     * Header: {"typ":"JWT","alg":"RS256"}
     * Claims: sub, roles, iss, iat, exp, rid, aud, jti, mth, pth
     *
     * @param userId         authenticated user UUID
     * @param roles          comma-delimited roles or role string
     * @param requestId      request correlation ID
     * @param targetAudience logical target service name (canonical audience)
     * @param method         downstream HTTP request method (e.g. GET, POST, DELETE)
     * @param targetPath     downstream canonical request path (e.g. /api/v1/documents)
     * @return 3-part compact JWT string, or null if assertion signing is disabled/unconfigured or inputs invalid
     */
    public String createAssertion(String userId, String roles, String requestId, String targetAudience, String method, String targetPath) {
        if (!this.enabled || this.privateKey == null) {
            log.warn("Cannot generate gateway assertion: RS256 private key is not configured or assertion signing is disabled");
            return null;
        }

        if (userId == null || userId.isBlank()) {
            log.warn("Cannot generate gateway assertion: userId is missing or blank");
            return null;
        }

        if (targetAudience == null || targetAudience.isBlank()) {
            log.warn("Cannot generate gateway assertion: targetAudience is missing or blank");
            return null;
        }

        if (method == null || method.isBlank()) {
            log.warn("Cannot generate gateway assertion: method is missing or blank");
            return null;
        }

        String rawMethod = method.trim();
        if (rawMethod.length() != method.length() || rawMethod.contains(" ") || rawMethod.contains("\t") || rawMethod.contains("\n")) {
            log.warn("Cannot generate gateway assertion: method contains forbidden whitespace padding: '{}'", method);
            return null;
        }

        String safeMethod = rawMethod.toUpperCase();
        if (!VALID_HTTP_METHODS.contains(safeMethod)) {
            log.warn("Cannot generate gateway assertion: invalid HTTP method verb '{}'", safeMethod);
            return null;
        }

        if (targetPath == null || targetPath.isBlank()) {
            log.warn("Cannot generate gateway assertion: targetPath is missing or blank");
            return null;
        }

        String rawPath = targetPath.trim();
        if (rawPath.length() != targetPath.length() || rawPath.contains(" ") || rawPath.contains("\t") || rawPath.contains("\n")) {
            log.warn("Cannot generate gateway assertion: targetPath contains forbidden whitespace padding: '{}'", targetPath);
            return null;
        }

        if (!rawPath.startsWith("/")) {
            log.warn("Cannot generate gateway assertion: targetPath must be an absolute path starting with '/': '{}'", targetPath);
            return null;
        }

        String safePath = normalizePath(rawPath);

        long now = Instant.now().getEpochSecond();
        long exp = now + VALIDITY_SECONDS;
        String safeRoles = (roles != null) ? roles : "";
        String safeRequestId = (requestId != null) ? requestId : "";
        String safeAudience = targetAudience.trim();
        String jti = UUID.randomUUID().toString();

        try {
            return Jwts.builder()
                    .header()
                        .add("typ", "JWT")
                        .add("alg", "RS256")
                    .and()
                    .id(jti)
                    .issuer(ISSUER)
                    .subject(userId)
                    .audience().single(safeAudience)
                    .claim("roles", safeRoles)
                    .claim("rid", safeRequestId)
                    .claim("iat", now)
                    .claim("exp", exp)
                    .claim("mth", safeMethod)
                    .claim("pth", safePath)
                    .signWith(this.privateKey, Jwts.SIG.RS256)
                    .compact();
        } catch (Exception e) {
            log.error("Failed to sign Gateway RS256 assertion for user {}: {}", sanitizeForLog(userId), e.getMessage());
            throw new IllegalStateException("Failed to generate signed RS256 Gateway assertion", e);
        }
    }

    /**
     * Canonicalizes downstream path: resolves dot segments and duplicate slashes while preserving trailing slashes.
     */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        boolean hasTrailingSlash = path.endsWith("/") && path.length() > 1;
        try {
            java.net.URI uri = java.net.URI.create("http://localhost" + (path.startsWith("/") ? path : "/" + path));
            String normalized = uri.normalize().getPath();
            if (normalized == null || normalized.isEmpty()) {
                normalized = "/";
            }
            if (hasTrailingSlash && !normalized.endsWith("/")) {
                normalized = normalized + "/";
            }
            return normalized;
        } catch (Exception e) {
            String clean = path.replaceAll("/{2,}", "/");
            if (!clean.startsWith("/")) {
                clean = "/" + clean;
            }
            return clean;
        }
    }


    /**
     * Resolves and loads an RSA private key from a configured path (classpath: or file system).
     * Fails closed if the path is missing, unreadable, or invalid.
     */
    public static PrivateKey resolveAndLoadPrivateKey(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException("Gateway assertion signing is enabled, but gateway.assertion.private-key-path is not configured");
        }

        String trimmed = configuredPath.trim();
        if (trimmed.startsWith("classpath:")) {
            String resourcePath = trimmed.substring("classpath:".length()).trim();
            if (resourcePath.startsWith("/")) {
                resourcePath = resourcePath.substring(1);
            }
            InputStream is = GatewayAssertionService.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                throw new IllegalStateException("Configured Gateway assertion private key classpath resource not found: " + trimmed);
            }
            try (is) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                PrivateKey pk = loadPrivateKeyFromPem(content);
                log.info("Loaded Gateway assertion private key from classpath: {}", trimmed);
                return pk;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse Gateway assertion private key from classpath: " + trimmed, e);
            }
        }

        Path path = Paths.get(trimmed);
        if (!path.isAbsolute()) {
            String baseDir = System.getProperty("omragul.base-dir", System.getenv("OMRAGUL_BASE_DIR"));
            if (baseDir != null && !baseDir.isBlank()) {
                path = Paths.get(baseDir.trim()).resolve(path).normalize();
            } else {
                path = Paths.get("").toAbsolutePath().resolve(path).normalize();
            }
        }

        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("Configured Gateway assertion private key file not found or unreadable: " + path.toAbsolutePath());
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            PrivateKey pk = loadPrivateKeyFromPem(content);
            log.info("Loaded Gateway assertion private key from: {}", path.toAbsolutePath());
            return pk;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gateway assertion private key from " + path.toAbsolutePath(), e);
        }
    }

    /**
     * Parses PKCS#8 PEM content into an RSA PrivateKey.
     */
    public static PrivateKey loadPrivateKeyFromPem(String pemContent) throws Exception {
        if (pemContent == null || pemContent.isBlank()) {
            throw new IllegalArgumentException("Private key PEM content is empty");
        }
        String cleanPem = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decodedKey = Base64.getDecoder().decode(cleanPem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    private static String sanitizeForLog(String input) {
        if (input == null) return "";
        return input.replaceAll("[\r\n]", "");
    }
}
