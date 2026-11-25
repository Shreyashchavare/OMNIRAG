package com.omragul.knowledge.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CrossLanguageAssertionVerifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, String> pythonFixtures;
    private GatewayAssertionVerifier verifier;
    private String testSecret;

    @BeforeEach
    void setUp() throws Exception {
        if (System.getProperty("cross.lang.hmac.fixture") == null) {
            generateFixturesViaPython();
        }
        File fixturesFile = new File("../../target/cross_language_fixtures/python_to_java_assertions.json")
                .getCanonicalFile();
        assertThat(fixturesFile).exists();
        pythonFixtures = objectMapper.readValue(fixturesFile, new TypeReference<Map<String, String>>() {
        });
        testSecret = pythonFixtures.get("test_secret");
        verifier = new GatewayAssertionVerifier("", objectMapper);
    }

    private void generateFixturesViaPython() throws Exception {
        File rootDir = new File("../..").getCanonicalFile();
        File genScript = new File(rootDir, "ai-services/agent-service/tests/test_cross_language_interop.py");
        if (!genScript.exists()) {
            return;
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
        process.waitFor();
    }

    @Test
    @DisplayName("Direction B: Python-generated legacy HMAC assertion is strictly rejected by Java Knowledge verifier")
    void testPythonValidAssertionAccepted() {
        String token = pythonFixtures.get("valid_token");
        String expectedRid = pythonFixtures.get("expected_rid");

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, expectedRid));
        assertThat(ex.getMessage()).contains("Legacy HMAC gateway assertions are rejected; RS256 assertions required");
    }

    @Test
    @DisplayName("Direction B: Python-generated tampered payload is rejected by Java verifier")
    void testPythonTamperedPayloadRejected() {
        String token = pythonFixtures.get("tampered_payload_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python-generated tampered signature is rejected by Java verifier")
    void testPythonTamperedSignatureRejected() {
        String token = pythonFixtures.get("tampered_signature_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python-generated invalid signature length is rejected by Java verifier")
    void testPythonInvalidSignatureLengthRejected() {
        String token = pythonFixtures.get("invalid_sig_length_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python-generated wrong issuer is rejected by Java verifier")
    void testPythonWrongIssuerRejected() {
        String token = pythonFixtures.get("wrong_issuer_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python-generated invalid UUID is rejected by Java verifier")
    void testPythonInvalidUuidRejected() {
        String token = pythonFixtures.get("invalid_uuid_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python-generated missing RID HMAC assertion is rejected as legacy HMAC in Java verifier")
    void testPythonMissingRidBehaviorInJava() {
        String token = pythonFixtures.get("missing_rid_token");
        String rid = pythonFixtures.get("expected_rid");
        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
        assertThat(ex.getMessage()).contains("Legacy HMAC gateway assertions are rejected; RS256 assertions required");
    }

    @Test
    @DisplayName("Direction B: Python-generated expired token is rejected by Java verifier")
    void testPythonExpiredTokenRejected() {
        String token = pythonFixtures.get("expired_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python-generated future token is rejected by Java verifier")
    void testPythonFutureTokenRejected() {
        String token = pythonFixtures.get("future_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, rid));
    }

    @Test
    @DisplayName("Direction B: Python-generated assertion with mismatched request ID is rejected by Java verifier")
    void testPythonMismatchedRequestIdRejected() {
        String token = pythonFixtures.get("mismatched_rid_token");
        assertThrows(GatewayAssertionException.class, () -> verifier.verify(token, "different-request-id-456"));
    }

    @Test
    @DisplayName("Direction B: Missing verification secret fails closed in Java verifier")
    void testMissingVerificationSecretFailsClosed() {
        GatewayAssertionVerifier unconfiguredVerifier = new GatewayAssertionVerifier("", objectMapper);
        String token = pythonFixtures.get("valid_token");
        String rid = pythonFixtures.get("expected_rid");
        assertThrows(GatewayAssertionException.class, () -> unconfiguredVerifier.verify(token, rid));
    }
}
