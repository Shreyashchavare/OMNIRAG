package com.omragul.processing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayAssertionVerifierTest {

    private static final String SECRET = "test-internal-service-secret-12345";
    private static KeyPair testKeyPair;
    private static KeyPair rogueKeyPair;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GatewayAssertionVerifier verifier;

    @BeforeAll
    static void initKeyPairs() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testKeyPair = kpg.generateKeyPair();
        rogueKeyPair = kpg.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        verifier = new GatewayAssertionVerifier(testKeyPair.getPublic(), objectMapper);
    }

    private String buildLegacyHmacToken(String sub, String roles, String iss, long iat, long exp, String rid,
            String signSecret) throws Exception {
        String json = String.format(
                "{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                sub, roles, iss, iat, exp, rid);
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmacBytes = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
        String signatureHex = HexFormat.of().formatHex(hmacBytes);

        return payloadB64 + "." + signatureHex;
    }

    private String buildRs256Token(String sub, String roles, String iss, long iat, long exp, String rid,
            PrivateKey signKey) {
        return buildRs256Token(sub, roles, iss, "processing-service", iat, exp, rid, UUID.randomUUID().toString(), signKey);
    }

    private String buildRs256Token(String sub, String roles, String iss, String aud, long iat, long exp, String rid,
            PrivateKey signKey) {
        return buildRs256Token(sub, roles, iss, aud, iat, exp, rid, UUID.randomUUID().toString(), signKey);
    }

    private String buildRs256Token(String sub, String roles, String iss, String aud, long iat, long exp, String rid,
            String jti, PrivateKey signKey) {
        return buildRs256Token(sub, roles, iss, aud, iat, exp, rid, jti, "GET", "/api/v1/processing", signKey);
    }

    private String buildRs256Token(String sub, String roles, String iss, String aud, long iat, long exp, String rid,
            String jti, String mth, String pth, PrivateKey signKey) {
        var builder = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer(iss)
                .subject(sub)
                .claim("roles", roles)
                .claim("rid", rid)
                .claim("iat", iat)
                .claim("exp", exp);
        if (aud != null) {
            builder.claim("aud", aud);
        }
        if (jti != null) {
            builder.id(jti);
        }
        if (mth != null) {
            builder.claim("mth", mth);
        }
        if (pth != null) {
            builder.claim("pth", pth);
        }
        return builder.signWith(signKey, Jwts.SIG.RS256).compact();
    }

    // =========================================================================
    // 1. RS256 Verification Tests
    // =========================================================================

    @Test
    @DisplayName("1. Valid RS256 token is accepted and claims are extracted")
    void testValidRs256TokenAccepted() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER,ROLE_ADMIN", "gateway-service", now, now + 60,
                "req-1", testKeyPair.getPrivate());

        GatewayAssertion assertion = verifier.verify(token, "req-1");
        assertThat(assertion).isNotNull();
        assertThat(assertion.userId()).isEqualTo(userId);
        assertThat(assertion.roles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(assertion.requestId()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("2. Invalid RS256 signature (signed with wrong key) is rejected")
    void testInvalidRs256SignatureRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", now, now + 60, "req-1",
                rogueKeyPair.getPrivate());

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("3. RS256 token with tampered payload is rejected")
    void testTamperedRs256PayloadRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", now, now + 60, "req-1",
                testKeyPair.getPrivate());
        String[] parts = token.split("\\.");

        String tamperedPayloadJson = String.format(
                "{\"sub\":\"%s\",\"roles\":\"ROLE_ADMIN\",\"iss\":\"gateway-service\",\"aud\":\"processing-service\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\"}",
                UUID.randomUUID(), now, now + 60);
        String tamperedB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayloadJson.getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedB64 + "." + parts[2];

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(tamperedToken, "req-1"));
    }

    @Test
    @DisplayName("4. RS256 token with tampered header is rejected")
    void testTamperedRs256HeaderRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", now, now + 60, "req-1",
                testKeyPair.getPrivate());
        String[] parts = token.split("\\.");

        String tamperedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"extra\":\"tampered\"}".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = tamperedHeader + "." + parts[1] + "." + parts[2];

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(tamperedToken, "req-1"));
    }

    @Test
    @DisplayName("5. Expired RS256 token beyond clock skew is rejected")
    void testExpiredRs256TokenRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", now - 200, now - 100, "req-1",
                testKeyPair.getPrivate());

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("6. RS256 token with future iat beyond clock skew is rejected")
    void testFutureIatRs256TokenRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", now + 100, now + 200, "req-1",
                testKeyPair.getPrivate());

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("7. RS256 token with non-UUID subject is rejected")
    void testInvalidNonUuidSubRs256Rejected() {
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token("not-a-valid-uuid", "ROLE_USER", "gateway-service", now, now + 60, "req-1",
                testKeyPair.getPrivate());

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("8. RS256 token missing required claims is rejected")
    void testMissingRequiredClaimsRs256Rejected() {
        long now = Instant.now().getEpochSecond();

        // Missing sub
        String noSubToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .id(UUID.randomUUID().toString())
                .issuer("gateway-service")
                .audience().single("processing-service")
                .claim("rid", "req-1")
                .claim("iat", now)
                .claim("exp", now + 60)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(noSubToken, "req-1"));

        // Missing rid
        String noRidToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .id(UUID.randomUUID().toString())
                .issuer("gateway-service")
                .audience().single("processing-service")
                .subject(UUID.randomUUID().toString())
                .claim("iat", now)
                .claim("exp", now + 60)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(noRidToken, "req-1"));

        // Missing aud
        String noAudToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .id(UUID.randomUUID().toString())
                .issuer("gateway-service")
                .subject(UUID.randomUUID().toString())
                .claim("rid", "req-1")
                .claim("iat", now)
                .claim("exp", now + 60)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(noAudToken, "req-1"));

        // Missing jti
        String noJtiToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer("gateway-service")
                .audience().single("processing-service")
                .subject(UUID.randomUUID().toString())
                .claim("rid", "req-1")
                .claim("iat", now)
                .claim("exp", now + 60)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(noJtiToken, "req-1"));
    }

    @Test
    @DisplayName("9. RS256 token with wrong issuer is rejected")
    void testWrongIssuerRs256Rejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "rogue-service", now, now + 60, "req-1",
                testKeyPair.getPrivate());

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("10. RS256 token with alg none is rejected")
    void testAlgNoneRs256Rejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"processing-service\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".";

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("11. RS256 token with alg HS256 is rejected (algorithm confusion prevention)")
    void testAlgHs256Rs256Rejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"processing-service\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String sig = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("fake-hs256-signature-bytes".getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + "." + sig;

        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("12. Malformed 3-part token is rejected and MUST NOT fall back to HMAC")
    void testMalformed3PartNeverFallsBackToHmac() {
        String token = "malformed.three.part";
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("16. Public key not configured fails closed for RS256 token")
    void testPublicKeyNotConfiguredFailsClosed() {
        GatewayAssertionVerifier noKeyVerifier = new GatewayAssertionVerifier((PublicKey) null, true, SECRET,
                objectMapper);
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", now, now + 60, "req-1",
                testKeyPair.getPrivate());

        assertThrows(GatewayAssertionException.class, () -> noKeyVerifier.verify(token, "req-1"));
    }

    @Test
    @DisplayName("RS256 token with missing typ header is rejected")
    void testMissingTypRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"processing-service\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("Invalid assertion type: expected JWT");
    }

    @Test
    @DisplayName("RS256 token with null typ header is rejected")
    void testNullTypRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":null}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"processing-service\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("Invalid assertion type: expected JWT");
    }

    @Test
    @DisplayName("RS256 token with non-string typ header is rejected")
    void testNonStringTypRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":123}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"processing-service\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("Invalid assertion type: expected JWT");
    }

    @Test
    @DisplayName("RS256 token with invalid string typ header is rejected")
    void testInvalidStringTypRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWE\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"processing-service\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("Invalid assertion type: expected JWT");
    }

    // =========================================================================
    // Audience Validation Tests (Stage 3.6 Batch 4.3A)
    // =========================================================================

    @Test
    @DisplayName("Audience: Matching canonical audience (processing-service) is accepted")
    void testAudienceMatchingAccepted() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service", now,
                now + 60, "req-aud-1", testKeyPair.getPrivate());

        GatewayAssertion assertion = verifier.verify(token, "req-aud-1");
        assertThat(assertion).isNotNull();
        assertThat(assertion.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Audience: Wrong audience (knowledge-service) is rejected fail-closed")
    void testAudienceWrongServiceRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "knowledge-service", now,
                now + 60, "req-aud-2", testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-aud-2"));
        assertThat(ex.getMessage())
                .contains("Invalid gateway assertion audience: expected processing-service, got knowledge-service");
    }

    @Test
    @DisplayName("Audience: Missing audience claim is rejected fail-closed")
    void testAudienceMissingRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", null, now, now + 60,
                "req-aud-3", testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-aud-3"));
        assertThat(ex.getMessage()).contains("Missing required claim 'aud'");
    }

    @Test
    @DisplayName("Audience: Empty audience string is rejected fail-closed")
    void testAudienceEmptyRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-aud-4\",\"jti\":\"test-jti-12345678\",\"mth\":\"GET\",\"pth\":\"/api/v1/processing\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-aud-4"));
        assertThat(ex.getMessage()).contains("Missing or empty assertion audience (aud)");
    }

    @Test
    @DisplayName("Audience: Whitespace audience string is rejected fail-closed")
    void testAudienceWhitespaceRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"   \",\"iat\":%d,\"exp\":%d,\"rid\":\"req-aud-5\",\"jti\":\"test-jti-12345678\",\"mth\":\"GET\",\"pth\":\"/api/v1/processing\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-aud-5"));
        assertThat(ex.getMessage()).contains("Missing or empty assertion audience (aud)");
    }

    @Test
    @DisplayName("Audience: Multi-audience array is rejected fail-closed (single string policy)")
    void testAudienceMultiArrayRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":[\"processing-service\",\"knowledge-service\"],\"iat\":%d,\"exp\":%d,\"rid\":\"req-aud-6\",\"jti\":\"test-jti-12345678\",\"mth\":\"GET\",\"pth\":\"/api/v1/processing\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-aud-6"));
        assertThat(ex.getMessage())
                .contains("Invalid assertion audience: multi-audience array not allowed (expected single string)");
    }

    @Test
    @DisplayName("Audience: Object audience is rejected fail-closed (non-string)")
    void testAudienceObjectRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":{\"name\":\"processing-service\"},\"iat\":%d,\"exp\":%d,\"rid\":\"req-aud-7\",\"jti\":\"test-jti-12345678\",\"mth\":\"GET\",\"pth\":\"/api/v1/processing\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-aud-7"));
        assertThat(ex.getMessage()).contains("Invalid assertion audience: expected string, got object");
    }

    @Test
    @DisplayName("Audience: Custom expected-audience configuration works as configured")
    void testCustomExpectedAudience() {
        GatewayAssertionVerifier customVerifier = new GatewayAssertionVerifier(testKeyPair.getPublic(),
                "custom-service", objectMapper);
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();

        String matchToken = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "custom-service", now,
                now + 60, "req-aud-8", testKeyPair.getPrivate());
        GatewayAssertion assertion = customVerifier.verify(matchToken, "req-aud-8");
        assertThat(assertion).isNotNull();

        String psToken = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service", now,
                now + 60, "req-aud-9", testKeyPair.getPrivate());
        assertThrows(GatewayAssertionException.class, () -> customVerifier.verify(psToken, "req-aud-9"));
    }

    @Test
    @DisplayName("Audience: Whitespace-padded audience string is rejected by pre-validation without trimming")
    void testAudienceWhitespacePaddedRejected() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\" processing-service \",\"iat\":%d,\"exp\":%d,\"rid\":\"req-aud-pad\",\"jti\":\"test-jti-12345678\",\"mth\":\"GET\",\"pth\":\"/api/v1/processing\"}",
                        UUID.randomUUID(), Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-aud-pad"));
        assertThat(ex.getMessage()).contains("Invalid gateway assertion audience: expected processing-service, got  processing-service ");
    }

    @Test
    @DisplayName("Startup/config fails closed when expected-audience is configured as blank or mismatched")
    void testBlankConfiguredAudienceThrowsException() {
        assertThrows(IllegalStateException.class, () ->
                new GatewayAssertionVerifier("/any/path", "", objectMapper));
        assertThrows(IllegalStateException.class, () ->
                new GatewayAssertionVerifier("/any/path", "   ", objectMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new GatewayAssertionVerifier(testKeyPair.getPublic(), "", objectMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new GatewayAssertionVerifier(testKeyPair.getPublic(), "   ", objectMapper));
        assertThrows(IllegalStateException.class, () ->
                new GatewayAssertionVerifier("/any/path", "alien-service", objectMapper));
    }

    @Test
    @DisplayName("Configured missing public key path throws IllegalStateException at startup")
    void testMissingConfiguredPublicKeyThrows() {
        assertThrows(IllegalStateException.class,
                () -> new GatewayAssertionVerifier("/nonexistent/path/to/key.pem", objectMapper));
    }

    // =========================================================================
    // 2. Legacy HMAC Verification Removal Tests
    // =========================================================================

    @Test
    @DisplayName("13. Legacy HMAC token is strictly rejected with RS256 requirement message")
    void testLegacyHmacStrictlyRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildLegacyHmacToken(userId.toString(), "ROLE_USER,ROLE_ADMIN", "gateway-service", now, now + 60,
                "req-1", SECRET);

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("Legacy HMAC gateway assertions are rejected; RS256 assertions required");
    }

    @Test
    @DisplayName("14. Two-part assertion token is always rejected as legacy HMAC")
    void testTwoPartTokenRejected() throws Exception {
        GatewayAssertionVerifier verifierWithKey = new GatewayAssertionVerifier(testKeyPair.getPublic(), objectMapper);
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildLegacyHmacToken(userId.toString(), "ROLE_USER", "gateway-service", now, now + 60, "req-1",
                SECRET);

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifierWithKey.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("Legacy HMAC gateway assertions are rejected; RS256 assertions required");
    }

    @Test
    @DisplayName("15. Invalid legacy HMAC format (two parts) is rejected before signature check")
    void testInvalidLegacyHmacSignatureRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildLegacyHmacToken(userId.toString(), "ROLE_USER", "gateway-service", now, now + 60, "req-1",
                "wrong-secret-key");

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("Legacy HMAC gateway assertions are rejected; RS256 assertions required");
    }

    @Test
    @DisplayName("Malformed token without separator is rejected")
    void testMalformedTokenRejected() {
        assertThrows(GatewayAssertionException.class, () -> verifier.verify("invalid-token-without-dot", "req-1"));
    }

    @Test
    @DisplayName("Null or blank assertion header is rejected")
    void testNullOrBlankAssertionRejected() {
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(null, "req-1"));
        assertThrows(GatewayAssertionException.class, () -> verifier.verify("   ", "req-1"));
    }

    // =========================================================================
    // Request Binding Tests (mth and pth)
    // =========================================================================

    @Test
    @DisplayName("Request binding: valid matching method and path accepted")
    void testRequestBindingValidMatchingMethodAndPathAccepted() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-1", UUID.randomUUID().toString(), "POST", "/api/v1/processing/jobs", testKeyPair.getPrivate());

        GatewayAssertion assertion = verifier.verify(token, "req-bind-1", "POST", "/api/v1/processing/jobs");
        assertThat(assertion).isNotNull();
        assertThat(assertion.userId()).isEqualTo(userId);
        assertThat(assertion.method()).isEqualTo("POST");
        assertThat(assertion.path()).isEqualTo("/api/v1/processing/jobs");
    }

    @Test
    @DisplayName("Request binding: mismatched HTTP method rejected")
    void testRequestBindingMismatchedMethodRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-2", UUID.randomUUID().toString(), "GET", "/api/v1/processing/jobs", testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-bind-2", "DELETE", "/api/v1/processing/jobs"));
        assertThat(ex.getMessage()).contains("Invalid gateway assertion method");
    }

    @Test
    @DisplayName("Request binding: mismatched downstream path rejected")
    void testRequestBindingMismatchedPathRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-3", UUID.randomUUID().toString(), "GET", "/api/v1/processing/jobs", testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-bind-3", "GET", "/api/v1/other-endpoint"));
        assertThat(ex.getMessage()).contains("Invalid gateway assertion path");
    }

    @Test
    @DisplayName("Request binding: missing mth claim in assertion rejected")
    void testRequestBindingMissingMethodClaimRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-4", UUID.randomUUID().toString(), null, "/api/v1/processing/jobs", testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-bind-4", "GET", "/api/v1/processing/jobs"));
        assertThat(ex.getMessage()).contains("Missing required claim 'mth'");
    }

    @Test
    @DisplayName("Request binding: missing pth claim in assertion rejected")
    void testRequestBindingMissingPathClaimRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-5", UUID.randomUUID().toString(), "GET", null, testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-bind-5", "GET", "/api/v1/processing/jobs"));
        assertThat(ex.getMessage()).contains("Missing required claim 'pth'");
    }

    @Test
    @DisplayName("Request binding: whitespace-padded mth claim rejected")
    void testRequestBindingWhitespaceMethodRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-6", UUID.randomUUID().toString(), " GET ", "/api/v1/processing/jobs", testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-bind-6", "GET", "/api/v1/processing/jobs"));
        assertThat(ex.getMessage()).contains("whitespace padding not allowed");
    }

    @Test
    @DisplayName("Request binding: whitespace-padded pth claim rejected")
    void testRequestBindingWhitespacePathRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-7", UUID.randomUUID().toString(), "GET", " /api/v1/processing/jobs ", testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-bind-7", "GET", "/api/v1/processing/jobs"));
        assertThat(ex.getMessage()).contains("whitespace padding not allowed");
    }

    @Test
    @DisplayName("Request binding: case-mismatched path rejected")
    void testRequestBindingCaseMismatchedPathRejected() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-8", UUID.randomUUID().toString(), "GET", "/api/v1/processing/jobs", testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-bind-8", "GET", "/API/V1/PROCESSING/JOBS"));
        assertThat(ex.getMessage()).contains("Invalid gateway assertion path");
    }

    @Test
    @DisplayName("Request binding: path normalization matches dot segments and duplicate slashes")
    void testRequestBindingPathNormalization() {
        UUID userId = UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        String token = buildRs256Token(userId.toString(), "ROLE_USER", "gateway-service", "processing-service",
                now, now + 60, "req-bind-9", UUID.randomUUID().toString(), "GET", "/api/v1/processing/jobs", testKeyPair.getPrivate());

        GatewayAssertion assertion = verifier.verify(token, "req-bind-9", "GET", "//api///v1/../v1/processing/jobs");
        assertThat(assertion).isNotNull();
        assertThat(assertion.path()).isEqualTo("/api/v1/processing/jobs");
    }
}
