package com.omragul.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayAssertionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private String exportPrivateKeyToPem(PrivateKey privateKey) {
        String base64Key = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64Key + "\n-----END PRIVATE KEY-----\n";
    }

    @Test
    @DisplayName("createAssertion generates valid signed RS256 JWT assertion with typ=JWT and 60s TTL")
    void testCreateAssertionValid() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());

        String userId = UUID.randomUUID().toString();
        String roles = "ROLE_USER,ROLE_ADMIN";
        String requestId = "req-test-12345";

        long beforeTime = Instant.now().getEpochSecond();
        String assertion = service.createAssertion(userId, roles, requestId, "knowledge-service");
        long afterTime = Instant.now().getEpochSecond();

        assertThat(assertion).isNotNull();
        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(3);

        // Verify header
        byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
        JsonNode headerNode = objectMapper.readTree(headerBytes);
        assertThat(headerNode.get("alg").asText()).isEqualTo("RS256");
        assertThat(headerNode.get("typ").asText()).isEqualTo("JWT");

        // Verify payload JSON directly
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode payloadNode = objectMapper.readTree(payloadBytes);
        assertThat(payloadNode.get("sub").asText()).isEqualTo(userId);
        assertThat(payloadNode.get("roles").asText()).isEqualTo("ROLE_USER,ROLE_ADMIN");
        assertThat(payloadNode.get("iss").asText()).isEqualTo("gateway-service");
        assertThat(payloadNode.get("rid").asText()).isEqualTo("req-test-12345");
        assertThat(payloadNode.has("jti")).isTrue();
        assertThat(payloadNode.get("jti").asText()).isNotBlank();
        assertThat(payloadNode.has("aud")).isTrue();
        assertThat(payloadNode.get("aud").isTextual()).isTrue();
        assertThat(payloadNode.get("aud").isArray()).isFalse();
        assertThat(payloadNode.get("aud").asText()).isEqualTo("knowledge-service");
        assertThat(payloadNode.has("mth")).isTrue();
        assertThat(payloadNode.get("mth").asText()).isEqualTo("GET");
        assertThat(payloadNode.has("pth")).isTrue();
        assertThat(payloadNode.get("pth").asText()).isEqualTo("/");

        long iat = payloadNode.get("iat").asLong();
        long exp = payloadNode.get("exp").asLong();
        assertThat(iat).isBetween(beforeTime, afterTime);
        assertThat(exp).isEqualTo(iat + 60L);

        // Verify cryptographic signature with JJWT and public key
        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .requireIssuer("gateway-service")
                .requireAudience("knowledge-service")
                .requireSubject(userId)
                .build()
                .parseSignedClaims(assertion)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("roles", String.class)).isEqualTo("ROLE_USER,ROLE_ADMIN");
        assertThat(claims.get("rid", String.class)).isEqualTo("req-test-12345");
        assertThat(claims.getIssuer()).isEqualTo("gateway-service");
        assertThat(claims.getAudience()).contains("knowledge-service");
    }

    @Test
    @DisplayName("JWT aud claim is serialized as single JSON string not an array for all canonical audiences")
    void testAudienceSerializationIsSingleJsonStringNotArray() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());

        String[] canonicalAudiences = {
                "knowledge-service",
                "processing-service",
                "agent-service",
                "rag-service"
        };

        for (String expectedAud : canonicalAudiences) {
            String token = service.createAssertion(UUID.randomUUID().toString(), "ROLE_USER", "req-1", expectedAud);
            assertThat(token).isNotNull();

            String[] parts = token.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payloadNode = objectMapper.readTree(payloadBytes);

            assertThat(payloadNode.has("aud")).isTrue();
            assertThat(payloadNode.get("aud").isTextual())
                    .as("aud claim must be a textual JSON string, not an array or object")
                    .isTrue();
            assertThat(payloadNode.get("aud").isArray())
                    .as("aud claim must NOT be a JSON array")
                    .isFalse();
            assertThat(payloadNode.get("aud").isObject())
                    .as("aud claim must NOT be a JSON object")
                    .isFalse();
            assertThat(payloadNode.get("aud").asText()).isEqualTo(expectedAud);
        }
    }

    @Test
    @DisplayName("createAssertion returns null when targetAudience is missing or blank")
    void testCreateAssertionMissingAudience() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());

        assertThat(service.createAssertion(UUID.randomUUID().toString(), "ROLE_USER", "req-1", null)).isNull();
        assertThat(service.createAssertion(UUID.randomUUID().toString(), "ROLE_USER", "req-1", "")).isNull();
        assertThat(service.createAssertion(UUID.randomUUID().toString(), "ROLE_USER", "req-1", "   ")).isNull();
    }

    @Test
    @DisplayName("createAssertion handles null roles and requestId safely")
    void testCreateAssertionNullRolesAndRequestId() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());

        String userId = UUID.randomUUID().toString();
        String assertion = service.createAssertion(userId, null, null, "knowledge-service");

        assertThat(assertion).isNotNull();
        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(3);

        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(assertion)
                .getPayload();

        assertThat(claims.get("roles", String.class)).isEqualTo("");
        assertThat(claims.get("rid", String.class)).isEqualTo("");
    }

    @Test
    @DisplayName("createAssertion returns null when userId is missing or blank")
    void testCreateAssertionMissingUserId() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());

        assertThat(service.createAssertion(null, "ROLE_USER", "req-1", "knowledge-service")).isNull();
        assertThat(service.createAssertion("", "ROLE_USER", "req-1", "knowledge-service")).isNull();
        assertThat(service.createAssertion("   ", "ROLE_USER", "req-1", "knowledge-service")).isNull();
    }

    @Test
    @DisplayName("createAssertion returns null when assertion signing is disabled")
    void testCreateAssertionDisabled() {
        GatewayProperties properties = new GatewayProperties();
        properties.getAssertion().setEnabled(false);
        GatewayAssertionService service = new GatewayAssertionService(properties);

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.getPrivateKey()).isNull();
        assertThat(service.createAssertion(UUID.randomUUID().toString(), "ROLE_USER", "req-1", "knowledge-service")).isNull();
    }

    @Test
    @DisplayName("Startup fails closed when assertion enabled but private key path is not configured")
    void testStartupFailsClosedWhenKeyPathMissing() {
        GatewayProperties properties = new GatewayProperties();
        properties.getAssertion().setEnabled(true);
        properties.getAssertion().setPrivateKeyPath(null);

        assertThatThrownBy(() -> new GatewayAssertionService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Gateway assertion signing is enabled, but gateway.assertion.private-key-path is not configured");

        properties.getAssertion().setPrivateKeyPath("");
        assertThatThrownBy(() -> new GatewayAssertionService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Gateway assertion signing is enabled, but gateway.assertion.private-key-path is not configured");
    }

    @Test
    @DisplayName("Startup fails closed when configured private key file does not exist")
    void testStartupFailsClosedWhenKeyFileNotFound() {
        GatewayProperties properties = new GatewayProperties();
        properties.getAssertion().setEnabled(true);
        properties.getAssertion().setPrivateKeyPath("/non/existent/path/to/private_key.pem");

        assertThatThrownBy(() -> new GatewayAssertionService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configured Gateway assertion private key file not found or unreadable");
    }

    @Test
    @DisplayName("Startup fails closed when configured private key file is malformed")
    void testStartupFailsClosedWhenKeyFileMalformed(@TempDir Path tempDir) throws Exception {
        Path malformedFile = tempDir.resolve("malformed_key.pem");
        Files.writeString(malformedFile, "-----BEGIN PRIVATE KEY-----\nNOT_VALID_BASE64\n-----END PRIVATE KEY-----");

        GatewayProperties properties = new GatewayProperties();
        properties.getAssertion().setEnabled(true);
        properties.getAssertion().setPrivateKeyPath(malformedFile.toAbsolutePath().toString());

        assertThatThrownBy(() -> new GatewayAssertionService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse Gateway assertion private key");
    }

    @Test
    @DisplayName("Startup succeeds and loads valid private key from file path")
    void testStartupSucceedsWithValidKeyFile(@TempDir Path tempDir) throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String pem = exportPrivateKeyToPem(keyPair.getPrivate());
        Path keyFile = tempDir.resolve("valid_gateway_key.pem");
        Files.writeString(keyFile, pem, StandardCharsets.UTF_8);

        GatewayProperties properties = new GatewayProperties();
        properties.getAssertion().setEnabled(true);
        properties.getAssertion().setPrivateKeyPath(keyFile.toAbsolutePath().toString());

        GatewayAssertionService service = new GatewayAssertionService(properties);
        assertThat(service.isEnabled()).isTrue();
        assertThat(service.getPrivateKey()).isNotNull();

        String token = service.createAssertion(UUID.randomUUID().toString(), "ROLE_USER", "req-abc", "knowledge-service");
        assertThat(token).isNotNull();

        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertThat(claims.getIssuer()).isEqualTo("gateway-service");
    }

    @Test
    @DisplayName("No HMAC fallback occurs when RS256 is used")
    void testNoHmacFallback() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayProperties properties = new GatewayProperties();
        properties.setInternalServiceSecret("test-secret-should-never-be-used");
        GatewayAssertionService service = new GatewayAssertionService(properties, keyPair.getPrivate());

        String token = service.createAssertion(UUID.randomUUID().toString(), "ROLE_USER", "req-1", "knowledge-service");
        assertThat(token).isNotNull();
        // Strict 3-part RS256 token, NOT 2-part HMAC
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).doesNotContain("test-secret");
        assertThat(parts[1]).doesNotContain("test-secret");
        assertThat(parts[2]).doesNotContain("test-secret");
    }

    @Test
    @DisplayName("Fresh, unpredictable jti generated for every assertion even with identical user and request ID")
    void testFreshJtiGeneratedForEveryAssertion() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());

        String userId = UUID.randomUUID().toString();
        String requestId = "req-identical-id";
        String token1 = service.createAssertion(userId, "ROLE_USER", requestId, "agent-service");
        String token2 = service.createAssertion(userId, "ROLE_USER", requestId, "agent-service");

        assertThat(token1).isNotNull();
        assertThat(token2).isNotNull();
        assertThat(token1).isNotEqualTo(token2);

        Claims claims1 = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token1).getPayload();
        Claims claims2 = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token2).getPayload();

        assertThat(claims1.getId()).isNotBlank();
        assertThat(claims2.getId()).isNotBlank();
        assertThat(claims1.getId()).isNotEqualTo(claims2.getId());

        // Validate that jti is a structurally valid UUID
        UUID.fromString(claims1.getId());
        UUID.fromString(claims2.getId());
    }

    @Test
    @DisplayName("createAssertion binds custom HTTP method and downstream path into signed JWT claims")
    void testCreateAssertionCustomMethodAndPath() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());

        String userId = UUID.randomUUID().toString();
        String token = service.createAssertion(userId, "ROLE_USER", "req-bind-1", "knowledge-service", "POST", "/api/v1/documents");

        assertThat(token).isNotNull();
        String[] parts = token.split("\\.");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode payloadNode = objectMapper.readTree(payloadBytes);

        assertThat(payloadNode.get("mth").asText()).isEqualTo("POST");
        assertThat(payloadNode.get("pth").asText()).isEqualTo("/api/v1/documents");

        Claims claims = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token).getPayload();
        assertThat(claims.get("mth", String.class)).isEqualTo("POST");
        assertThat(claims.get("pth", String.class)).isEqualTo("/api/v1/documents");
    }

    @Test
    @DisplayName("createAssertion rejects invalid HTTP method verbs and whitespace-padded methods")
    void testCreateAssertionInvalidMethod() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());
        String userId = UUID.randomUUID().toString();

        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", null, "/api/v1/test")).isNull();
        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", "", "/api/v1/test")).isNull();
        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", "   ", "/api/v1/test")).isNull();
        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", "INVALID_METHOD", "/api/v1/test")).isNull();
        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", " GET ", "/api/v1/test")).isNull();
    }

    @Test
    @DisplayName("createAssertion rejects invalid paths and whitespace-padded paths")
    void testCreateAssertionInvalidPath() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());
        String userId = UUID.randomUUID().toString();

        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", "GET", null)).isNull();
        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", "GET", "")).isNull();
        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", "GET", "   ")).isNull();
        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", "GET", " /api/v1/test ")).isNull();
        assertThat(service.createAssertion(userId, "ROLE_USER", "req-1", "agent-service", "GET", "relative/path")).isNull();
    }

    @Test
    @DisplayName("createAssertion normalizes path dot segments and duplicate slashes while preserving trailing slashes")
    void testCreateAssertionPathNormalization() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GatewayAssertionService service = new GatewayAssertionService(keyPair.getPrivate());
        String userId = UUID.randomUUID().toString();

        // Duplicate slashes
        String token1 = service.createAssertion(userId, "ROLE_USER", "req-1", "knowledge-service", "GET", "//api///v1//documents");
        Claims claims1 = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token1).getPayload();
        assertThat(claims1.get("pth", String.class)).isEqualTo("/api/v1/documents");

        // Dot segments resolved
        String token2 = service.createAssertion(userId, "ROLE_USER", "req-2", "knowledge-service", "GET", "/api/v1/../v1/documents");
        Claims claims2 = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token2).getPayload();
        assertThat(claims2.get("pth", String.class)).isEqualTo("/api/v1/documents");

        // Trailing slash preserved
        String token3 = service.createAssertion(userId, "ROLE_USER", "req-3", "knowledge-service", "GET", "/knowledge-bases/");
        Claims claims3 = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token3).getPayload();
        assertThat(claims3.get("pth", String.class)).isEqualTo("/knowledge-bases/");
    }
}
