package com.omragul.knowledge.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Service
public class InternalAuthService {

    private static final Set<String> FORBIDDEN_DEFAULTS = Set.of(
            "omragul-internal-service-secret-v2",
            "omragul-internal-dev-secret-v2",
            "changeme",
            "secret",
            "password",
            "default",
            "placeholder",
            "replace_with_a_strong_random_secret"
    );

    private final String internalSecret;

    public InternalAuthService(
            @Value("${internal.service.secret:}") String internalSecret
    ) {
        this.internalSecret = internalSecret;
    }

    @PostConstruct
    public void validateConfiguration() {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException(
                    "INTERNAL_SERVICE_SECRET configuration is missing or blank. Application startup aborted."
            );
        }
        if (FORBIDDEN_DEFAULTS.contains(internalSecret.trim().toLowerCase())) {
            throw new IllegalStateException(
                    "INTERNAL_SERVICE_SECRET is set to an insecure or known development default. Application startup aborted."
            );
        }
    }

    public boolean isAuthorizedInternalService(String secretHeader) {
        if (secretHeader == null || secretHeader.isBlank()) {
            return false;
        }
        if (internalSecret == null || internalSecret.isBlank()) {
            return false;
        }

        byte[] expectedBytes = internalSecret.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = secretHeader.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }
}
