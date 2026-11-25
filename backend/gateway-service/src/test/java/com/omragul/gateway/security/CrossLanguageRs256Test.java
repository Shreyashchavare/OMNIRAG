package com.omragul.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CrossLanguageRs256Test {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String signSha256WithRsa(String data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private String exportPublicKeyToPem(KeyPair keyPair) {
        String base64Key = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64Key + "\n-----END PUBLIC KEY-----\n";
    }

    @Test
    @DisplayName("Direction A: Java JJWT RS256 assertions verified by Python Agent & RAG auth services")
    void testJavaToPythonRs256Interop() throws Exception {
        // 1. Generate ephemeral RSA keypairs
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair testKeyPair = kpg.generateKeyPair();
        KeyPair rogueKeyPair = kpg.generateKeyPair();

        String expectedSub = UUID.randomUUID().toString();
        String expectedRoles = "ROLE_USER,ROLE_ADMIN";
        String expectedRid = "req-cross-rs256-java-to-py";
        String expectedIss = "gateway-service";
        long now = Instant.now().getEpochSecond();

        // 2. Generate valid token with production GatewayAssertionService
        GatewayAssertionService assertionService = new GatewayAssertionService(testKeyPair.getPrivate());
        String validTokenAgent = assertionService.createAssertion(expectedSub, expectedRoles, expectedRid, "agent-service", "POST", "/api/v1/agent/chat");
        String validTokenRag = assertionService.createAssertion(expectedSub, expectedRoles, expectedRid, "rag-service", "POST", "/api/v1/rag/search");
        String validToken = validTokenAgent;

        // 3. Generate negative test tokens
        // Tampered signature
        String[] validParts = validToken.split("\\.");
        String tamperedSigToken = validParts[0] + "." + validParts[1] + "." + validParts[2].substring(0, validParts[2].length() - 4) + "AAAA";

        // Tampered payload
        String tamperedPayloadJson = String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":\"agent-service\",\"jti\":\"%s\",\"mth\":\"POST\",\"pth\":\"/api/v1/agent/chat\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                UUID.randomUUID(), expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid);
        String tamperedPayloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadJson.getBytes(StandardCharsets.UTF_8));
        String tamperedPayloadToken = validParts[0] + "." + tamperedPayloadB64 + "." + validParts[2];

        // Wrong key
        String wrongKeyToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer(expectedIss)
                .audience().single("agent-service")
                .subject(expectedSub)
                .id(UUID.randomUUID().toString())
                .claim("roles", expectedRoles)
                .claim("rid", expectedRid)
                .claim("mth", "POST")
                .claim("pth", "/api/v1/agent/chat")
                .claim("iat", now)
                .claim("exp", now + 3600)
                .signWith(rogueKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Wrong issuer
        String wrongIssuerToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer("untrusted-service")
                .audience().single("agent-service")
                .subject(expectedSub)
                .id(UUID.randomUUID().toString())
                .claim("roles", expectedRoles)
                .claim("rid", expectedRid)
                .claim("mth", "POST")
                .claim("pth", "/api/v1/agent/chat")
                .claim("iat", now)
                .claim("exp", now + 3600)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Expired token
        String expiredToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer(expectedIss)
                .audience().single("agent-service")
                .subject(expectedSub)
                .id(UUID.randomUUID().toString())
                .claim("roles", expectedRoles)
                .claim("rid", expectedRid)
                .claim("mth", "POST")
                .claim("pth", "/api/v1/agent/chat")
                .claim("iat", now - 3600)
                .claim("exp", now - 60)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Future token
        String futureToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer(expectedIss)
                .audience().single("agent-service")
                .subject(expectedSub)
                .id(UUID.randomUUID().toString())
                .claim("roles", expectedRoles)
                .claim("rid", expectedRid)
                .claim("mth", "POST")
                .claim("pth", "/api/v1/agent/chat")
                .claim("iat", now + 3600)
                .claim("exp", now + 7200)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Invalid non-UUID subject
        String invalidUuidToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer(expectedIss)
                .audience().single("agent-service")
                .subject("not-a-valid-uuid")
                .id(UUID.randomUUID().toString())
                .claim("roles", expectedRoles)
                .claim("rid", expectedRid)
                .claim("mth", "POST")
                .claim("pth", "/api/v1/agent/chat")
                .claim("iat", now)
                .claim("exp", now + 3600)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Missing rid
        String missingRidToken = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer(expectedIss)
                .audience().single("agent-service")
                .subject(expectedSub)
                .id(UUID.randomUUID().toString())
                .claim("roles", expectedRoles)
                .claim("mth", "POST")
                .claim("pth", "/api/v1/agent/chat")
                .claim("iat", now)
                .claim("exp", now + 3600)
                .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Missing typ header
        String missingTypHeaderJson = "{\"alg\":\"RS256\"}";
        String missingTypHeaderB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(missingTypHeaderJson.getBytes(StandardCharsets.UTF_8));
        String validPayloadB64 = validParts[1];
        String missingTypData = missingTypHeaderB64 + "." + validPayloadB64;
        String missingTypToken = missingTypData + "." + signSha256WithRsa(missingTypData, testKeyPair.getPrivate());

        // Invalid typ header
        String invalidTypHeaderJson = "{\"alg\":\"RS256\",\"typ\":\"INVALID\"}";
        String invalidTypHeaderB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(invalidTypHeaderJson.getBytes(StandardCharsets.UTF_8));
        String invalidTypData = invalidTypHeaderB64 + "." + validPayloadB64;
        String invalidTypToken = invalidTypData + "." + signSha256WithRsa(invalidTypData, testKeyPair.getPrivate());

        // alg=HS256
        String hs256HeaderJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String hs256HeaderB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hs256HeaderJson.getBytes(StandardCharsets.UTF_8));
        String algHs256Token = hs256HeaderB64 + "." + validPayloadB64 + ".fakesignaturehmac";

        // alg=none
        String noneHeaderJson = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String noneHeaderB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(noneHeaderJson.getBytes(StandardCharsets.UTF_8));
        String algNoneToken = noneHeaderB64 + "." + validPayloadB64 + ".";

        // Audience negative cases
        String wrongAudienceToken = assertionService.createAssertion(expectedSub, expectedRoles, expectedRid, "knowledge-service");

        String arrayAudienceHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String arrayAudiencePayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":[\"agent-service\",\"rag-service\"],\"jti\":\"%s\",\"mth\":\"POST\",\"pth\":\"/api/v1/agent/chat\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String arrayAudienceData = arrayAudienceHeader + "." + arrayAudiencePayload;
        String arrayAudienceToken = arrayAudienceData + "." + signSha256WithRsa(arrayAudienceData, testKeyPair.getPrivate());

        String missingAudienceHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String missingAudiencePayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"jti\":\"%s\",\"mth\":\"POST\",\"pth\":\"/api/v1/agent/chat\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String missingAudienceData = missingAudienceHeader + "." + missingAudiencePayload;
        String missingAudienceToken = missingAudienceData + "." + signSha256WithRsa(missingAudienceData, testKeyPair.getPrivate());

        String paddedAudienceHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String paddedAudiencePayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":\" agent-service \",\"jti\":\"%s\",\"mth\":\"POST\",\"pth\":\"/api/v1/agent/chat\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String paddedAudienceData = paddedAudienceHeader + "." + paddedAudiencePayload;
        String paddedAudienceToken = paddedAudienceData + "." + signSha256WithRsa(paddedAudienceData, testKeyPair.getPrivate());

        // Missing jti
        String missingJtiHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String missingJtiPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":\"agent-service\",\"mth\":\"POST\",\"pth\":\"/api/v1/agent/chat\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String missingJtiData = missingJtiHeader + "." + missingJtiPayload;
        String missingJtiToken = missingJtiData + "." + signSha256WithRsa(missingJtiData, testKeyPair.getPrivate());

        // Method & path negative tokens
        String wrongMethodToken = assertionService.createAssertion(expectedSub, expectedRoles, expectedRid, "agent-service", "DELETE", "/api/v1/agent/chat");
        String wrongPathToken = assertionService.createAssertion(expectedSub, expectedRoles, expectedRid, "agent-service", "POST", "/api/v1/wrong");

        String missingMethodHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String missingMethodPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":\"agent-service\",\"jti\":\"%s\",\"pth\":\"/api/v1/agent/chat\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String missingMethodData = missingMethodHeader + "." + missingMethodPayload;
        String missingMethodToken = missingMethodData + "." + signSha256WithRsa(missingMethodData, testKeyPair.getPrivate());

        String missingPathHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String missingPathPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":\"agent-service\",\"jti\":\"%s\",\"mth\":\"POST\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String missingPathData = missingPathHeader + "." + missingPathPayload;
        String missingPathToken = missingPathData + "." + signSha256WithRsa(missingPathData, testKeyPair.getPrivate());

        String whitespaceMethodHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String whitespaceMethodPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":\"agent-service\",\"jti\":\"%s\",\"mth\":\" POST \",\"pth\":\"/api/v1/agent/chat\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String whitespaceMethodData = whitespaceMethodHeader + "." + whitespaceMethodPayload;
        String whitespaceMethodToken = whitespaceMethodData + "." + signSha256WithRsa(whitespaceMethodData, testKeyPair.getPrivate());

        String whitespacePathHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String whitespacePathPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":\"agent-service\",\"jti\":\"%s\",\"mth\":\"POST\",\"pth\":\" /api/v1/agent/chat \",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String whitespacePathData = whitespacePathHeader + "." + whitespacePathPayload;
        String whitespacePathToken = whitespacePathData + "." + signSha256WithRsa(whitespacePathData, testKeyPair.getPrivate());

        String caseMismatchedPathHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String caseMismatchedPathPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"aud\":\"agent-service\",\"jti\":\"%s\",\"mth\":\"POST\",\"pth\":\"/API/V1/AGENT/CHAT\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                        expectedSub, expectedRoles, expectedIss, UUID.randomUUID(), now, now + 3600, expectedRid).getBytes(StandardCharsets.UTF_8));
        String caseMismatchedPathData = caseMismatchedPathHeader + "." + caseMismatchedPathPayload;
        String caseMismatchedPathToken = caseMismatchedPathData + "." + signSha256WithRsa(caseMismatchedPathData, testKeyPair.getPrivate());

        // Assemble fixture
        Map<String, String> fixture = new HashMap<>();
        fixture.put("public_key_pem", exportPublicKeyToPem(testKeyPair));
        fixture.put("expected_sub", expectedSub);
        fixture.put("expected_roles", expectedRoles);
        fixture.put("expected_rid", expectedRid);
        fixture.put("expected_iss", expectedIss);
        fixture.put("valid_token", validToken);
        fixture.put("valid_token_agent", validTokenAgent);
        fixture.put("valid_token_rag", validTokenRag);
        fixture.put("expected_method_agent", "POST");
        fixture.put("expected_path_agent", "/api/v1/agent/chat");
        fixture.put("expected_method_rag", "POST");
        fixture.put("expected_path_rag", "/api/v1/rag/search");
        fixture.put("wrong_method_token", wrongMethodToken);
        fixture.put("wrong_path_token", wrongPathToken);
        fixture.put("missing_method_token", missingMethodToken);
        fixture.put("missing_path_token", missingPathToken);
        fixture.put("whitespace_method_token", whitespaceMethodToken);
        fixture.put("whitespace_path_token", whitespacePathToken);
        fixture.put("case_mismatched_path_token", caseMismatchedPathToken);
        fixture.put("wrong_audience_token", wrongAudienceToken);
        fixture.put("array_audience_token", arrayAudienceToken);
        fixture.put("missing_audience_token", missingAudienceToken);
        fixture.put("padded_audience_token", paddedAudienceToken);
        fixture.put("missing_jti_token", missingJtiToken);
        fixture.put("tampered_signature_token", tamperedSigToken);
        fixture.put("tampered_payload_token", tamperedPayloadToken);
        fixture.put("wrong_key_token", wrongKeyToken);
        fixture.put("wrong_issuer_token", wrongIssuerToken);
        fixture.put("expired_token", expiredToken);
        fixture.put("future_token", futureToken);
        fixture.put("invalid_uuid_token", invalidUuidToken);
        fixture.put("missing_rid_token", missingRidToken);
        fixture.put("missing_typ_token", missingTypToken);
        fixture.put("invalid_typ_token", invalidTypToken);
        fixture.put("alg_hs256_token", algHs256Token);
        fixture.put("alg_none_token", algNoneToken);

        File tempFixture = File.createTempFile("java_to_py_rs256_", ".json");
        try {
            objectMapper.writeValue(tempFixture, fixture);

            // Locate verify_rs256_bridge.py
            File rootDir = new File("../..").getCanonicalFile();
            File bridgeScript = new File(rootDir, "ai-services/agent-service/tests/verify_rs256_bridge.py").getCanonicalFile();
            assertThat(bridgeScript).exists();

            List<String> command = new ArrayList<>();
            // Use py -3.10 on Windows if available, fallback to python
            command.add("py");
            command.add("-3.10");
            command.add(bridgeScript.getAbsolutePath());
            command.add(tempFixture.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(rootDir);
            pb.environment().put("OMRAGUL_TEST_MODE", "true");
            pb.redirectErrorStream(true);

            Process process;
            try {
                process = pb.start();
            } catch (Exception ex) {
                // Fallback to python directly if py is unavailable
                command.set(0, "python");
                command.remove(1); // remove -3.10
                pb = new ProcessBuilder(command);
                pb.directory(rootDir);
                pb.redirectErrorStream(true);
                process = pb.start();
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            assertThat(exitCode)
                    .withFailMessage("Python bridge verification failed with exit code %d:\n%s", exitCode, output.toString())
                    .isEqualTo(0);

            assertThat(output.toString()).contains("SUCCESS: All Java-generated RS256 assertions verified successfully across Agent and RAG services.");
        } finally {
            if (tempFixture.exists()) {
                tempFixture.delete();
            }
        }
    }
}
