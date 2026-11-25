package com.omragul.processing.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Verifies gateway assertion tokens propagated by Gateway Service.
 * Accepts exclusively standard RS256 JWT tokens; legacy HMAC tokens are strictly rejected.
 */
@Slf4j
@Component
public class GatewayAssertionVerifier {

    private static final long MAX_CLOCK_SKEW_SECONDS = 10L;
    private static final String EXPECTED_ISSUER = "gateway-service";
    public static final String DEFAULT_AUDIENCE = "processing-service";
    public static final Set<String> VALID_HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
    );

    private final String publicKeyPath;
    private final String expectedAudience;
    private final GatewayAssertionReplayService replayService;
    private final ObjectMapper objectMapper;
    private PublicKey publicKey;

    @Autowired
    public GatewayAssertionVerifier(
            @Value("${gateway.assertion.public-key-path:${GATEWAY_ASSERTION_PUBLIC_KEY_PATH:}}") String publicKeyPath,
            @Value("${gateway.assertion.expected-audience:${GATEWAY_ASSERTION_EXPECTED_AUDIENCE:processing-service}}") String expectedAudience,
            @Autowired(required = false) GatewayAssertionReplayService replayService,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.publicKeyPath = publicKeyPath;
        if (expectedAudience != null && expectedAudience.trim().isEmpty()) {
            throw new IllegalStateException("Configured Gateway assertion expected audience cannot be blank");
        }
        if (expectedAudience != null && !DEFAULT_AUDIENCE.equals(expectedAudience.trim())) {
            throw new IllegalStateException("Configured Gateway assertion expected audience must match canonical identity '" + DEFAULT_AUDIENCE + "', got: " + expectedAudience);
        }
        this.expectedAudience = (expectedAudience != null) ? expectedAudience.trim() : DEFAULT_AUDIENCE;
        this.replayService = replayService;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.publicKey = resolveAndLoadPublicKey(publicKeyPath);
    }

    public GatewayAssertionVerifier(
            @Value("${gateway.assertion.public-key-path:${GATEWAY_ASSERTION_PUBLIC_KEY_PATH:}}") String publicKeyPath,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this(publicKeyPath, DEFAULT_AUDIENCE, null, objectMapper);
    }

    public GatewayAssertionVerifier(
            String publicKeyPath,
            String expectedAudience,
            ObjectMapper objectMapper
    ) {
        this(publicKeyPath, expectedAudience, null, objectMapper);
    }

    /**
     * Programmatic/test constructor with explicit PublicKey, expectedAudience, and replayService.
     */
    public GatewayAssertionVerifier(
            PublicKey publicKey,
            String expectedAudience,
            GatewayAssertionReplayService replayService,
            ObjectMapper objectMapper
    ) {
        this.publicKeyPath = null;
        if (expectedAudience != null && expectedAudience.trim().isEmpty()) {
            throw new IllegalArgumentException("Expected audience cannot be blank");
        }
        this.expectedAudience = (expectedAudience != null) ? expectedAudience.trim() : DEFAULT_AUDIENCE;
        this.publicKey = publicKey;
        this.replayService = replayService;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    public GatewayAssertionVerifier(
            PublicKey publicKey,
            String expectedAudience,
            ObjectMapper objectMapper
    ) {
        this(publicKey, expectedAudience, null, objectMapper);
    }

    public GatewayAssertionVerifier(
            PublicKey publicKey,
            ObjectMapper objectMapper
    ) {
        this(publicKey, DEFAULT_AUDIENCE, objectMapper);
    }

    public GatewayAssertionVerifier(PublicKey publicKey) {
        this(publicKey, DEFAULT_AUDIENCE, new ObjectMapper());
    }

    /**
     * Backward-compatible constructor for existing tests during migration.
     */
    public GatewayAssertionVerifier(
            PublicKey publicKey,
            boolean legacyHmacEnabled,
            String internalSecret,
            ObjectMapper objectMapper
    ) {
        this(publicKey, DEFAULT_AUDIENCE, objectMapper);
    }

    public String getExpectedAudience() {
        return this.expectedAudience;
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public static PublicKey resolveAndLoadPublicKey(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            log.warn("Gateway assertion public key path not configured; RS256 assertions will fail closed");
            return null;
        }

        String trimmed = configuredPath.trim();
        if (trimmed.startsWith("classpath:")) {
            String resourcePath = trimmed.substring("classpath:".length()).trim();
            if (resourcePath.startsWith("/")) {
                resourcePath = resourcePath.substring(1);
            }
            InputStream is = GatewayAssertionVerifier.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                throw new IllegalStateException("Configured Gateway assertion public key classpath resource not found: " + trimmed);
            }
            try (is) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                PublicKey pk = loadPublicKeyFromPem(content);
                log.info("Loaded Gateway assertion public key from classpath: {}", trimmed);
                return pk;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse Gateway assertion public key from classpath: " + trimmed, e);
            }
        }

        Path path = Paths.get(trimmed);
        if (!path.isAbsolute()) {
            // Deterministic base directory resolution:
            // Prefer OMRAGUL_BASE_DIR system property or environment variable if set;
            // otherwise resolve strictly against current working directory.
            String baseDir = System.getProperty("omragul.base-dir", System.getenv("OMRAGUL_BASE_DIR"));
            if (baseDir != null && !baseDir.isBlank()) {
                path = Paths.get(baseDir.trim()).resolve(path).normalize();
            } else {
                path = Paths.get("").toAbsolutePath().resolve(path).normalize();
            }
        }

        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("Configured Gateway assertion public key file not found or unreadable: " + path.toAbsolutePath());
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            PublicKey pk = loadPublicKeyFromPem(content);
            log.info("Loaded Gateway assertion public key from: {}", path.toAbsolutePath());
            return pk;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gateway assertion public key from " + path.toAbsolutePath(), e);
        }
    }

    public static PublicKey tryLoadPublicKeyFromPath(String pathStr) {
        return resolveAndLoadPublicKey(pathStr);
    }

    public static PublicKey loadPublicKeyFromPem(String pemContent) throws Exception {
        if (pemContent == null || pemContent.isBlank()) {
            throw new IllegalArgumentException("Public key PEM content is empty");
        }
        String cleanPem = pemContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decodedKey = Base64.getDecoder().decode(cleanPem);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    public GatewayAssertion verify(String assertionHeader, String requestCorrelationId) {
        return verify(assertionHeader, requestCorrelationId, null, null);
    }

    public GatewayAssertion verify(String assertionHeader, String requestCorrelationId, String expectedMethod, String expectedPath) {
        if (assertionHeader == null || assertionHeader.isBlank()) {
            throw new GatewayAssertionException("Missing gateway assertion header");
        }

        String trimmed = assertionHeader.trim();
        String[] parts = trimmed.split("\\.");

        if (parts.length == 3) {
            // Standard RS256 JWT path - NEVER falls back to HMAC
            return verifyRs256(trimmed, parts, requestCorrelationId, expectedMethod, expectedPath);
        } else if (parts.length == 2) {
            // Legacy HMAC assertions are rejected
            throw new GatewayAssertionException("Legacy HMAC gateway assertions are rejected; RS256 assertions required");
        } else {
            throw new GatewayAssertionException("Malformed gateway assertion structure (expected 3 parts for RS256)");
        }
    }

    private GatewayAssertion verifyRs256(String jwtToken, String[] parts, String requestCorrelationId, String expectedMethod, String expectedPath) {
        // 1. Inspect unverified header strictly: require alg == RS256
        byte[] headerBytes;
        try {
            headerBytes = Base64.getUrlDecoder().decode(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new GatewayAssertionException("Malformed Base64URL in assertion header", e);
        }

        JsonNode headerNode;
        try {
            headerNode = objectMapper.readTree(headerBytes);
        } catch (Exception e) {
            throw new GatewayAssertionException("Malformed JSON in assertion header", e);
        }

        JsonNode algNode = headerNode.get("alg");
        if (algNode == null || algNode.isNull() || !algNode.isTextual() || !"RS256".equals(algNode.asText().trim())) {
            throw new GatewayAssertionException("Invalid assertion algorithm: expected RS256, got " + (algNode != null && !algNode.isNull() ? algNode.asText() : "none"));
        }

        JsonNode typNode = headerNode.get("typ");
        if (typNode == null || typNode.isNull() || !typNode.isTextual() || !"JWT".equals(typNode.asText().trim())) {
            throw new GatewayAssertionException("Invalid assertion type: expected JWT, got " + (typNode != null && !typNode.isNull() ? typNode.asText() : "none"));
        }

        // 2. Public key check
        if (this.publicKey == null) {
            throw new GatewayAssertionException("Gateway assertion public key not configured for RS256 verification");
        }

        // 3. Expected audience configuration check
        if (this.expectedAudience == null || this.expectedAudience.isBlank()) {
            throw new GatewayAssertionException("Gateway assertion expected audience not configured");
        }

        // 4. Inspect payload JSON for strict claim presence, audience string format, and integer timestamp types
        byte[] payloadBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new GatewayAssertionException("Malformed Base64URL in assertion payload", e);
        }

        JsonNode payloadNode;
        try {
            payloadNode = objectMapper.readTree(payloadBytes);
        } catch (Exception e) {
            throw new GatewayAssertionException("Malformed JSON in assertion payload", e);
        }

        for (String reqClaim : List.of("sub", "iss", "iat", "exp", "rid", "aud", "mth", "pth")) {
            if (!payloadNode.has(reqClaim) || payloadNode.get(reqClaim).isNull()) {
                throw new GatewayAssertionException("Missing required claim '" + reqClaim + "' in gateway assertion");
            }
        }

        JsonNode audNode = payloadNode.get("aud");
        if (!audNode.isTextual()) {
            if (audNode.isArray()) {
                throw new GatewayAssertionException("Invalid assertion audience: multi-audience array not allowed (expected single string)");
            } else if (audNode.isObject()) {
                throw new GatewayAssertionException("Invalid assertion audience: expected string, got object");
            } else {
                throw new GatewayAssertionException("Invalid assertion audience: expected single string, got " + audNode.getNodeType());
            }
        }

        String rawAud = audNode.asText();
        if (rawAud == null || rawAud.isBlank()) {
            throw new GatewayAssertionException("Missing or empty assertion audience (aud)");
        }
        if (!this.expectedAudience.equals(rawAud)) {
            throw new GatewayAssertionException("Invalid gateway assertion audience: expected " + this.expectedAudience + ", got " + rawAud);
        }

        JsonNode iatNode = payloadNode.get("iat");
        JsonNode expNode = payloadNode.get("exp");
        if (!iatNode.isIntegralNumber()) {
            throw new GatewayAssertionException("Invalid assertion iat timestamp: must be integer epoch seconds");
        }
        if (!expNode.isIntegralNumber()) {
            throw new GatewayAssertionException("Invalid assertion exp timestamp: must be integer epoch seconds");
        }

        long now = Instant.now().getEpochSecond();
        long iat = iatNode.asLong();
        long exp = expNode.asLong();

        if (iat > now + MAX_CLOCK_SKEW_SECONDS) {
            throw new GatewayAssertionException("Gateway assertion issued-at timestamp is in the future");
        }
        if (exp < now - MAX_CLOCK_SKEW_SECONDS) {
            throw new GatewayAssertionException("Gateway assertion has expired");
        }

        // Validate mth (method binding claim)
        JsonNode mthNode = payloadNode.get("mth");
        if (!mthNode.isTextual()) {
            throw new GatewayAssertionException("Invalid assertion method: expected string, got " + mthNode.getNodeType());
        }
        String rawMth = mthNode.asText();
        if (rawMth == null || rawMth.isBlank()) {
            throw new GatewayAssertionException("Missing or empty assertion method (mth)");
        }
        if (!rawMth.trim().equals(rawMth) || rawMth.contains(" ") || rawMth.contains("\t") || rawMth.contains("\n")) {
            throw new GatewayAssertionException("Invalid gateway assertion method: whitespace padding not allowed");
        }
        if (!VALID_HTTP_METHODS.contains(rawMth)) {
            throw new GatewayAssertionException("Invalid gateway assertion method verb: " + rawMth);
        }

        // Validate pth (path binding claim)
        JsonNode pthNode = payloadNode.get("pth");
        if (!pthNode.isTextual()) {
            throw new GatewayAssertionException("Invalid assertion path: expected string, got " + pthNode.getNodeType());
        }
        String rawPth = pthNode.asText();
        if (rawPth == null || rawPth.isBlank()) {
            throw new GatewayAssertionException("Missing or empty assertion path (pth)");
        }
        if (!rawPth.trim().equals(rawPth) || rawPth.contains(" ") || rawPth.contains("\t") || rawPth.contains("\n")) {
            throw new GatewayAssertionException("Invalid gateway assertion path: whitespace padding not allowed");
        }
        if (!rawPth.startsWith("/")) {
            throw new GatewayAssertionException("Invalid gateway assertion path: must start with '/'");
        }

        if (!payloadNode.has("jti") || payloadNode.get("jti").isNull()) {
            throw new GatewayAssertionException("Missing required claim 'jti' in gateway assertion");
        }

        JsonNode jtiNode = payloadNode.get("jti");
        if (!jtiNode.isTextual()) {
            throw new GatewayAssertionException("Invalid assertion jti: expected string, got " + jtiNode.getNodeType());
        }
        String rawJti = jtiNode.asText();
        if (rawJti == null || rawJti.isBlank()) {
            throw new GatewayAssertionException("Missing or empty assertion identifier (jti)");
        }
        if (!rawJti.trim().equals(rawJti) || rawJti.contains(" ")) {
            throw new GatewayAssertionException("Invalid gateway assertion jti: whitespace padding not allowed");
        }
        if (rawJti.length() < 16 || rawJti.length() > 128) {
            throw new GatewayAssertionException("Invalid gateway assertion jti length: " + rawJti.length());
        }

        // 5. Cryptographic signature, issuer, and audience verification with JJWT
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(this.publicKey)
                    .requireIssuer(EXPECTED_ISSUER)
                    .requireAudience(this.expectedAudience)
                    .clockSkewSeconds(MAX_CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(jwtToken)
                    .getPayload();
        } catch (io.jsonwebtoken.security.SignatureException e) {
            throw new GatewayAssertionException("Invalid gateway assertion signature", e);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new GatewayAssertionException("Gateway assertion has expired", e);
        } catch (io.jsonwebtoken.IncorrectClaimException e) {
            throw new GatewayAssertionException("Invalid gateway assertion claim: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new GatewayAssertionException("Gateway assertion verification failed: " + e.getMessage(), e);
        }

        // Validate jti, mth, and pth in signed claims match inspected payload
        String claimsJti = claims.getId();
        if (claimsJti == null || !claimsJti.equals(rawJti)) {
            throw new GatewayAssertionException("Gateway assertion jti claim mismatch or missing in signed claims");
        }
        String claimsMth = claims.get("mth", String.class);
        if (claimsMth == null || !claimsMth.equals(rawMth)) {
            throw new GatewayAssertionException("Gateway assertion mth claim mismatch or missing in signed claims");
        }
        String claimsPth = claims.get("pth", String.class);
        if (claimsPth == null || !claimsPth.equals(rawPth)) {
            throw new GatewayAssertionException("Gateway assertion pth claim mismatch or missing in signed claims");
        }

        // 6. Request Binding Validation (Method & Path) - MUST precede replay claim
        if (expectedMethod != null) {
            if (expectedMethod.isBlank()) {
                throw new GatewayAssertionException("Expected method cannot be blank");
            }
            String normalizedExpectedMethod = expectedMethod.trim().toUpperCase();
            if (!rawMth.equals(normalizedExpectedMethod)) {
                throw new GatewayAssertionException("Invalid gateway assertion method: expected " + normalizedExpectedMethod + ", got " + rawMth);
            }
        }

        if (expectedPath != null) {
            if (expectedPath.isBlank()) {
                throw new GatewayAssertionException("Expected path cannot be blank");
            }
            String normalizedExpectedPath = normalizePath(expectedPath);
            if (!rawPth.equals(normalizedExpectedPath)) {
                throw new GatewayAssertionException("Invalid gateway assertion path: expected " + normalizedExpectedPath + ", got " + rawPth);
            }
        }

        // 7. Atomic Redis Replay Verification
        if (this.replayService != null) {
            long remainingSeconds = (exp + MAX_CLOCK_SKEW_SECONDS) - now;
            if (remainingSeconds <= 0) {
                throw new GatewayAssertionException("Gateway assertion has expired");
            }
            long ttlSeconds = remainingSeconds + 5L; // 5s clock-skew/network latency safety margin
            ttlSeconds = Math.max(1L, Math.min(ttlSeconds, 300L));
            boolean claimed = this.replayService.claimJti(rawJti, ttlSeconds, this.expectedAudience, EXPECTED_ISSUER);
            if (!claimed) {
                throw new GatewayAssertionException("Gateway assertion replay detected: jti has already been used");
            }
        }

        // 8. Subject UUID validation
        String subStr = payloadNode.get("sub").asText().trim();
        if (subStr.isEmpty()) {
            throw new GatewayAssertionException("Missing assertion subject (sub)");
        }
        UUID userId;
        try {
            userId = UUID.fromString(subStr);
        } catch (IllegalArgumentException e) {
            throw new GatewayAssertionException("Invalid user ID UUID format in assertion: " + subStr, e);
        }

        // 9. Request ID correlation
        String rid = payloadNode.get("rid").asText().trim();
        if (rid.isEmpty()) {
            throw new GatewayAssertionException("Missing or empty request ID (rid) in gateway assertion");
        }
        if (requestCorrelationId != null && !requestCorrelationId.isBlank()) {
            if (!rid.equals(requestCorrelationId.trim())) {
                throw new GatewayAssertionException("Assertion request ID mismatch (assertion=" + rid + ", request=" + requestCorrelationId + ")");
            }
        }

        // 10. Parse roles
        JsonNode rolesNode = payloadNode.get("roles");
        Set<String> roles = Collections.emptySet();
        if (rolesNode != null && !rolesNode.asText().isBlank()) {
            roles = Arrays.stream(rolesNode.asText().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }

        return new GatewayAssertion(userId, roles, rid, rawJti, rawMth, rawPth);
    }

    /**
     * Canonicalizes path: resolves dot segments and duplicate slashes while preserving trailing slashes.
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
}
