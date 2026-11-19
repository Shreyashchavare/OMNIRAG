package com.omragul.processing.client;

import com.omragul.processing.client.impl.KnowledgeServiceClientImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeServiceClientConfigValidationTest {

    @Test
    @DisplayName("Startup validation - Fails when internal secret is null")
    void testStartupValidation_FailsOnNullSecret() {
        KnowledgeServiceClientImpl client = new KnowledgeServiceClientImpl(
                "http://localhost:8082",
                null,
                1000,
                2000);

        IllegalStateException ex = assertThrows(IllegalStateException.class, client::validateConfiguration);
        assertTrue(ex.getMessage().contains("missing or blank"));
    }

    @Test
    @DisplayName("Startup validation - Fails when internal secret is blank")
    void testStartupValidation_FailsOnBlankSecret() {
        KnowledgeServiceClientImpl client = new KnowledgeServiceClientImpl(
                "http://localhost:8082",
                "   ",
                1000,
                2000);

        IllegalStateException ex = assertThrows(IllegalStateException.class, client::validateConfiguration);
        assertTrue(ex.getMessage().contains("missing or blank"));
    }

    @Test
    @DisplayName("Startup validation - Fails on known development defaults")
    void testStartupValidation_FailsOnKnownDevDefaults() {
        String[] forbidden = {
                "omragul-internal-service-secret-v2",
                "omragul-internal-dev-secret-v2",
                "changeme",
                "secret",
                "password",
                "default",
                "placeholder",
                "replace_with_a_strong_random_secret"
        };

        for (String devSecret : forbidden) {
            KnowledgeServiceClientImpl client = new KnowledgeServiceClientImpl(
                    "http://localhost:8082",
                    devSecret,
                    1000,
                    2000);
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    client::validateConfiguration,
                    "Expected startup failure on Processing Service for known default: " + devSecret);
            assertEquals(
                    "INTERNAL_SERVICE_SECRET is set to an insecure or known development default for Processing Service. Application startup aborted.",
                    ex.getMessage(),
                    "Error message must be the static template and must not echo the secret"
            );
        }
    }

    @Test
    @DisplayName("Startup validation - Succeeds on valid configured secret")
    void testStartupValidation_SucceedsOnValidSecret() {
        KnowledgeServiceClientImpl client = new KnowledgeServiceClientImpl(
                "http://localhost:8082",
                "valid-secure-processing-secret-2026",
                1000,
                2000);

        assertDoesNotThrow(client::validateConfiguration);
    }

    @Test
    @DisplayName("Configuration compatibility - Same valid secret initializes cleanly on Processing Service")
    void testCompatibleConfiguration() {
        String sharedSecret = "omragul-shared-internal-cluster-key-xyz-777";

        KnowledgeServiceClientImpl client = new KnowledgeServiceClientImpl(
                "http://localhost:8082",
                sharedSecret,
                1000,
                2000);

        assertDoesNotThrow(client::validateConfiguration);
    }
}
