package com.omragul.knowledge.security;

import java.util.Set;
import java.util.UUID;

public record GatewayAssertion(
        UUID userId,
        Set<String> roles,
        String requestId,
        String jti,
        String method,
        String path
) {
    public GatewayAssertion(UUID userId, Set<String> roles, String requestId, String jti) {
        this(userId, roles, requestId, jti, null, null);
    }

    public GatewayAssertion(UUID userId, Set<String> roles, String requestId) {
        this(userId, roles, requestId, null, null, null);
    }
}
