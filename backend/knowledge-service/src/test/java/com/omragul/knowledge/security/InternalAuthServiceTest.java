package com.omragul.knowledge.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InternalAuthServiceTest {

    private static final String CONFIGURED_SECRET = "Omragul_Internal_SuperSecret_2026_SecureKey!";

    @Test
    @DisplayName("1. isAuthorizedInternalService - Accepts exact valid secret")
    void testAuthorizedInternalService_ValidSecret() {
        InternalAuthService service = new InternalAuthService(CONFIGURED_SECRET);
        service.validateConfiguration();

        assertTrue(service.isAuthorizedInternalService(CONFIGURED_SECRET));
    }

    @Test
    @DisplayName("2. isAuthorizedInternalService - Rejects incorrect secret")
    void testAuthorizedInternalService_IncorrectSecret() {
        InternalAuthService service = new InternalAuthService(CONFIGURED_SECRET);
        service.validateConfiguration();

        assertFalse(service.isAuthorizedInternalService("CompletelyWrongSecretValue123456789"));
    }

    @Test
    @DisplayName("3. isAuthorizedInternalService - Rejects null secret")
    void testAuthorizedInternalService_NullSecret() {
        InternalAuthService service = new InternalAuthService(CONFIGURED_SECRET);
        service.validateConfiguration();

        assertFalse(service.isAuthorizedInternalService(null));
    }

    @Test
    @DisplayName("4. isAuthorizedInternalService - Rejects empty secret")
    void testAuthorizedInternalService_EmptySecret() {
        InternalAuthService service = new InternalAuthService(CONFIGURED_SECRET);
        service.validateConfiguration();

        assertFalse(service.isAuthorizedInternalService(""));
    }

    @Test
    @DisplayName("5. isAuthorizedInternalService - Rejects whitespace-only secret")
    void testAuthorizedInternalService_WhitespaceOnlySecret() {
        InternalAuthService service = new InternalAuthService(CONFIGURED_SECRET);
        service.validateConfiguration();

        assertFalse(service.isAuthorizedInternalService("   "));
        assertFalse(service.isAuthorizedInternalService("\t\n"));
    }

    @Test
    @DisplayName("6. isAuthorizedInternalService - Rejects secret with incorrect length")
    void testAuthorizedInternalService_IncorrectLength() {
        InternalAuthService service = new InternalAuthService(CONFIGURED_SECRET);
        service.validateConfiguration();

        // Prefix only
        assertFalse(
                service.isAuthorizedInternalService(CONFIGURED_SECRET.substring(0, CONFIGURED_SECRET.length() - 5)));
        // With extra suffix
        assertFalse(service.isAuthorizedInternalService(CONFIGURED_SECRET + "_extra"));
    }

    @Test
    @DisplayName("7. isAuthorizedInternalService - Rejects case mismatch (exact matching required)")
    void testAuthorizedInternalService_CaseMismatch() {
        InternalAuthService service = new InternalAuthService(CONFIGURED_SECRET);
        service.validateConfiguration();

        // Lowercase variant
        assertFalse(service.isAuthorizedInternalService(CONFIGURED_SECRET.toLowerCase()));
        // Uppercase variant
        assertFalse(service.isAuthorizedInternalService(CONFIGURED_SECRET.toUpperCase()));
    }

    // Startup Configuration Validation Tests (Phase 3)

    @Test
    @DisplayName("Startup validation - Fails on missing/null secret")
    void testStartupValidation_FailsOnNullSecret() {
        InternalAuthService service = new InternalAuthService(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateConfiguration);
        assertTrue(ex.getMessage().contains("missing or blank"));
    }

    @Test
    @DisplayName("Startup validation - Fails on blank secret")
    void testStartupValidation_FailsOnBlankSecret() {
        InternalAuthService service = new InternalAuthService("   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateConfiguration);
        assertTrue(ex.getMessage().contains("missing or blank"));
    }

    @Test
    @DisplayName("Startup validation - Fails on known committed development default")
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
            InternalAuthService service = new InternalAuthService(devSecret);
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    service::validateConfiguration,
                    "Expected failure for known default: " + devSecret);
            assertEquals(
                    "INTERNAL_SERVICE_SECRET is set to an insecure or known development default. Application startup aborted.",
                    ex.getMessage(),
                    "Error message must be the static template and must not echo the secret"
            );
        }
    }

    @Test
    @DisplayName("Startup validation - Succeeds on valid configured secret")
    void testStartupValidation_SucceedsOnValidSecret() {
        InternalAuthService service = new InternalAuthService("a-strong-custom-production-secret-9941");
        assertDoesNotThrow(service::validateConfiguration);
    }
}
