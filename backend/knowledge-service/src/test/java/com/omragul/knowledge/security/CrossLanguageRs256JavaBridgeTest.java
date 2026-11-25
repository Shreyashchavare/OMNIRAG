package com.omragul.knowledge.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.PublicKey;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CrossLanguageRs256JavaBridgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, String> pythonFixtures;
    private GatewayAssertionVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        if (System.getProperty("cross.lang.fixture") == null) {
            generateFixturesViaPython();
        }
        File fixtureFile = resolveFixtureFile();
        assertThat(fixtureFile).exists();

        pythonFixtures = objectMapper.readValue(fixtureFile, new TypeReference<Map<String, String>>() {
        });
        String pem = pythonFixtures.get("public_key_pem");
        PublicKey publicKey = GatewayAssertionVerifier.loadPublicKeyFromPem(pem);

        verifier = new GatewayAssertionVerifier(publicKey, "knowledge-service", objectMapper);
    }

    private File resolveFixtureFile() {
        String customPath = System.getProperty("cross.lang.fixture");
        if (customPath != null && !customPath.isBlank()) {
            return new File(customPath);
        }
        File f1 = new File("../../target/cross_language_fixtures/python_to_java_rs256_assertions.json");
        if (f1.exists()) {
            return f1;
        }
        return new File("target/cross_language_fixtures/python_to_java_rs256_assertions.json");
    }

    private void generateFixturesViaPython() throws Exception {
        File rootDir = new File("../..").getCanonicalFile();
        File genScript = new File(rootDir, "ai-services/agent-service/tests/generate_python_rs256_fixtures.py");
        if (!genScript.exists()) {
            throw new IllegalStateException(
                    "Python fixture generator script not found at: " + genScript.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder("py", "-3.10", genScript.getAbsolutePath());
        pb.directory(rootDir);
        Process process;
        try {
            process = pb.start();
        } catch (Exception ex) {
            pb = new ProcessBuilder("python", genScript.getAbsolutePath());
            pb.directory(rootDir);
            process = pb.start();
        }
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("Failed to run Python fixture generator, exit code: " + code);
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated valid RS256 assertion accepted by Java Knowledge verifier")
    void testPythonValidRs256AssertionAccepted() {
        String token = pythonFixtures.getOrDefault("valid_token_knowledge", pythonFixtures.get("valid_token"));
        String expectedSub = pythonFixtures.get("expected_sub");
        String expectedRid = pythonFixtures.get("expected_rid");
        String expectedRoles = pythonFixtures.get("expected_roles");

        GatewayAssertion assertion = verifier.verify(token, expectedRid, "GET", "/api/v1/documents");
        assertThat(assertion).isNotNull();
        assertThat(assertion.userId()).isEqualTo(UUID.fromString(expectedSub));
        assertThat(assertion.requestId()).isEqualTo(expectedRid);
        assertThat(assertion.method()).isEqualTo("GET");
        assertThat(assertion.path()).isEqualTo("/api/v1/documents");
        for (String role : expectedRoles.split(",")) {
            assertThat(assertion.roles()).contains(role.trim());
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated tampered signature rejected by Java Knowledge verifier")
    void testPythonTamperedSignatureRejected() {
        String token = pythonFixtures.get("tampered_signature_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated tampered payload rejected by Java Knowledge verifier")
    void testPythonTamperedPayloadRejected() {
        String token = pythonFixtures.get("tampered_payload_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated wrong key rejected by Java Knowledge verifier")
    void testPythonWrongKeyRejected() {
        String token = pythonFixtures.get("wrong_key_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated wrong issuer rejected by Java Knowledge verifier")
    void testPythonWrongIssuerRejected() {
        String token = pythonFixtures.get("wrong_issuer_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated expired token rejected by Java Knowledge verifier")
    void testPythonExpiredTokenRejected() {
        String token = pythonFixtures.get("expired_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated future token rejected by Java Knowledge verifier")
    void testPythonFutureTokenRejected() {
        String token = pythonFixtures.get("future_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated invalid UUID sub rejected by Java Knowledge verifier")
    void testPythonInvalidUuidRejected() {
        String token = pythonFixtures.get("invalid_uuid_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated missing rid rejected by Java Knowledge verifier")
    void testPythonMissingRidRejected() {
        String token = pythonFixtures.get("missing_rid_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated missing typ header rejected by Java Knowledge verifier")
    void testPythonMissingTypRejected() {
        String token = pythonFixtures.get("missing_typ_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated invalid typ header rejected by Java Knowledge verifier")
    void testPythonInvalidTypRejected() {
        String token = pythonFixtures.get("invalid_typ_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated alg=HS256 rejected by Java Knowledge verifier")
    void testPythonAlgHs256Rejected() {
        String token = pythonFixtures.get("alg_hs256_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated alg=none rejected by Java Knowledge verifier")
    void testPythonAlgNoneRejected() {
        String token = pythonFixtures.get("alg_none_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated wrong audience rejected by Java Knowledge verifier")
    void testPythonWrongAudienceRejected() {
        String token = pythonFixtures.get("wrong_audience_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated missing audience rejected by Java Knowledge verifier")
    void testPythonMissingAudienceRejected() {
        String token = pythonFixtures.get("missing_audience_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated array audience rejected by Java Knowledge verifier")
    void testPythonArrayAudienceRejected() {
        String token = pythonFixtures.get("array_audience_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated padded audience rejected by Java Knowledge verifier")
    void testPythonPaddedAudienceRejected() {
        String token = pythonFixtures.get("padded_audience_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated missing jti rejected by Java Knowledge verifier")
    void testPythonMissingJtiRejected() {
        String token = pythonFixtures.get("missing_jti_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated processing token rejected by Java Knowledge verifier (cross-audience)")
    void testPythonCrossAudienceProcessingRejected() {
        String token = pythonFixtures.get("valid_token_processing");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated wrong method rejected by Java verifier")
    void testPythonWrongMethodRejected() {
        String token = pythonFixtures.get("wrong_method_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid, "GET", "/api/v1/documents"));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated wrong path rejected by Java verifier")
    void testPythonWrongPathRejected() {
        String token = pythonFixtures.get("wrong_path_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid, "GET", "/api/v1/documents"));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated missing method rejected by Java verifier")
    void testPythonMissingMethodRejected() {
        String token = pythonFixtures.get("missing_method_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid, "GET", "/api/v1/documents"));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated missing path rejected by Java verifier")
    void testPythonMissingPathRejected() {
        String token = pythonFixtures.get("missing_path_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid, "GET", "/api/v1/documents"));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated whitespace method rejected by Java verifier")
    void testPythonWhitespaceMethodRejected() {
        String token = pythonFixtures.get("whitespace_method_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid, "GET", "/api/v1/documents"));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated whitespace path rejected by Java verifier")
    void testPythonWhitespacePathRejected() {
        String token = pythonFixtures.get("whitespace_path_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid, "GET", "/api/v1/documents"));
        }
    }

    @Test
    @DisplayName("Direction B: Python PyJWT-generated case mismatched path rejected by Java verifier")
    void testPythonCaseMismatchedPathRejected() {
        String token = pythonFixtures.get("case_mismatched_path_token");
        if (token != null) {
            String rid = pythonFixtures.get("expected_rid");
            assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid, "GET", "/api/v1/documents"));
        }
    }
}
