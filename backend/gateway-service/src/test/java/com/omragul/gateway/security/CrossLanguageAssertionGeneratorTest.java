package com.omragul.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.gateway.config.GatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CrossLanguageAssertionGeneratorTest {

    public static final String TEST_SECRET = "omragul-test-cross-language-assertion-secret-2026";
    public static final String TEST_USER_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    public static final String TEST_ROLES = "ROLE_USER,ROLE_ADMIN";
    public static final String TEST_REQUEST_ID = "req-cross-lang-12345";
    public static final String TEST_ISSUER = "gateway-service";

    private String signHmacHex(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }

    private String createCustomToken(String sub, String roles, String iss, long iat, long exp, String rid, String secret) throws Exception {
        String jsonPayload = String.format(
                "{\"sub\":\"%s\",\"roles\":\"%s\",\"iss\":\"%s\",\"iat\":%d,\"exp\":%d,\"rid\":\"%s\"}",
                sub != null ? sub : "",
                roles != null ? roles : "",
                iss != null ? iss : "",
                iat,
                exp,
                rid != null ? rid : ""
        );
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
        String signatureHex = signHmacHex(payloadBase64, secret);
        return payloadBase64 + "." + signatureHex;
    }

    @Test
    @DisplayName("Generate Java assertions and verify them")
    void generateAndVerifyJavaAssertions() throws Exception {
        long now = Instant.now().getEpochSecond();

        // 1. Valid token generated using legacy HMAC format
        String validToken = createCustomToken(TEST_USER_ID, TEST_ROLES, TEST_ISSUER, now, now + 60, TEST_REQUEST_ID, TEST_SECRET);
        assertThat(validToken).isNotNull();

        // 2. Negative cases
        String expiredToken = createCustomToken(TEST_USER_ID, TEST_ROLES, TEST_ISSUER, now - 7200, now - 3600, TEST_REQUEST_ID, TEST_SECRET);
        String futureToken = createCustomToken(TEST_USER_ID, TEST_ROLES, TEST_ISSUER, now + 3600, now + 7200, TEST_REQUEST_ID, TEST_SECRET);
        String wrongIssuerToken = createCustomToken(TEST_USER_ID, TEST_ROLES, "rogue-service", now, now + 60, TEST_REQUEST_ID, TEST_SECRET);
        String invalidUuidToken = createCustomToken("not-a-valid-uuid", TEST_ROLES, TEST_ISSUER, now, now + 60, TEST_REQUEST_ID, TEST_SECRET);
        String missingRidToken = createCustomToken(TEST_USER_ID, TEST_ROLES, TEST_ISSUER, now, now + 60, "", TEST_SECRET);

        // Tampered payload
        String[] validParts = validToken.split("\\.");
        String tamperedPayloadJson = "{\"sub\":\"" + UUID.randomUUID() + "\",\"roles\":\"" + TEST_ROLES + "\",\"iss\":\"" + TEST_ISSUER + "\",\"iat\":" + now + ",\"exp\":" + (now + 60) + ",\"rid\":\"" + TEST_REQUEST_ID + "\"}";
        String tamperedPayloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadJson.getBytes(StandardCharsets.UTF_8));
        String tamperedPayloadToken = tamperedPayloadB64 + "." + validParts[1];

        // Tampered signature
        String tamperedSigToken = validParts[0] + "." + validParts[1].substring(0, 62) + "00";

        // Invalid signature length
        String invalidSigLengthToken = validParts[0] + "." + validParts[1].substring(0, 40);

        // Boolean timestamp
        String boolTimestampJson = "{\"sub\":\"" + TEST_USER_ID + "\",\"roles\":\"" + TEST_ROLES + "\",\"iss\":\"" + TEST_ISSUER + "\",\"iat\":true,\"exp\":" + (now + 60) + ",\"rid\":\"" + TEST_REQUEST_ID + "\"}";
        String boolTimestampB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(boolTimestampJson.getBytes(StandardCharsets.UTF_8));
        String boolTimestampToken = boolTimestampB64 + "." + signHmacHex(boolTimestampB64, TEST_SECRET);

        // Float timestamp
        String floatTimestampJson = "{\"sub\":\"" + TEST_USER_ID + "\",\"roles\":\"" + TEST_ROLES + "\",\"iss\":\"" + TEST_ISSUER + "\",\"iat\":12345.67,\"exp\":" + (now + 60) + ",\"rid\":\"" + TEST_REQUEST_ID + "\"}";
        String floatTimestampB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(floatTimestampJson.getBytes(StandardCharsets.UTF_8));
        String floatTimestampToken = floatTimestampB64 + "." + signHmacHex(floatTimestampB64, TEST_SECRET);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("test_secret", TEST_SECRET);
        tokens.put("expected_sub", TEST_USER_ID);
        tokens.put("expected_roles", TEST_ROLES);
        tokens.put("expected_rid", TEST_REQUEST_ID);
        tokens.put("expected_iss", TEST_ISSUER);
        tokens.put("valid_token", validToken);
        tokens.put("expired_token", expiredToken);
        tokens.put("future_token", futureToken);
        tokens.put("wrong_issuer_token", wrongIssuerToken);
        tokens.put("invalid_uuid_token", invalidUuidToken);
        tokens.put("missing_rid_token", missingRidToken);
        tokens.put("tampered_payload_token", tamperedPayloadToken);
        tokens.put("tampered_signature_token", tamperedSigToken);
        tokens.put("invalid_sig_length_token", invalidSigLengthToken);
        tokens.put("boolean_timestamp_token", boolTimestampToken);
        tokens.put("float_timestamp_token", floatTimestampToken);

        // Also write to target/cross_language_fixtures in root
        File outputFile = new File("../../target/cross_language_fixtures/java_to_python_assertions.json").getCanonicalFile();
        outputFile.getParentFile().mkdirs();
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outputFile, tokens);
        assertThat(outputFile).exists();
    }
}
