package com.omragul.knowledge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured security audit logger for privileged actions, cross-user overrides,
 * authorization rejections, and invalid authentication attempts.
 * Sanitized to never emit tokens or credentials.
 */
@Component
public class SecurityAuditLogger {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void logAccessDenied(UUID userId, String action, UUID targetResourceId, String reason) {
        AUDIT_LOG.warn("AUDIT_EVENT=ACCESS_DENIED timestamp=\"{}\" user_id=\"{}\" action=\"{}\" resource_id=\"{}\" reason=\"{}\"",
                Instant.now(), userId, sanitize(action), targetResourceId, sanitize(reason));
    }

    public void logAssertionRejected(String reason, String uri, String requestId) {
        AUDIT_LOG.warn("AUDIT_EVENT=ASSERTION_REJECTED timestamp=\"{}\" uri=\"{}\" request_id=\"{}\" reason=\"{}\"",
                Instant.now(), sanitize(uri), sanitize(requestId), sanitize(reason));
    }

    public void logAuthenticationFailure(String reason, String uri, String requestId) {
        AUDIT_LOG.warn("AUDIT_EVENT=AUTH_FAILURE timestamp=\"{}\" uri=\"{}\" request_id=\"{}\" reason=\"{}\"",
                Instant.now(), sanitize(uri), sanitize(requestId), sanitize(reason));
    }

    private String sanitize(String input) {
        if (input == null) return "none";
        return input.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
