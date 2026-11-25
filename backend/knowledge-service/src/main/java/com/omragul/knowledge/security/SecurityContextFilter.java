package com.omragul.knowledge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.knowledge.dto.ErrorResponseDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityContextFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_GATEWAY_ASSERTION = "X-Gateway-Assertion";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";
    public static final String HEADER_INTERNAL_SERVICE_SECRET = "X-Internal-Service-Secret";

    private final InternalAuthService internalAuthService;
    private final GatewayAssertionVerifier gatewayAssertionVerifier;
    private final SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper;

    public SecurityContextFilter(
            InternalAuthService internalAuthService,
            GatewayAssertionVerifier gatewayAssertionVerifier,
            SecurityAuditLogger securityAuditLogger,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.internalAuthService = internalAuthService;
        this.gatewayAssertionVerifier = gatewayAssertionVerifier;
        this.securityAuditLogger = securityAuditLogger;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String requestId = request.getHeader(HEADER_REQUEST_ID);

            // 1. Check for Internal Service credentials
            String secretHeader = request.getHeader(HEADER_INTERNAL_SECRET);
            if (secretHeader == null || secretHeader.isBlank()) {
                secretHeader = request.getHeader(HEADER_INTERNAL_SERVICE_SECRET);
            }

            if (secretHeader != null && !secretHeader.isBlank()) {
                if (!internalAuthService.isAuthorizedInternalService(secretHeader)) {
                    securityAuditLogger.logAuthenticationFailure("Invalid internal service credentials", request.getRequestURI(), requestId);
                    sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            "Invalid internal service credentials", request.getRequestURI());
                    return;
                }

                // Ensure internal-secret authorization does not accidentally bypass user-level authorization (Requirement 6)
                if (!isAuthorizedInternalEndpoint(request)) {
                    securityAuditLogger.logAuthenticationFailure("Internal service credentials not authorized for user-facing endpoint", request.getRequestURI(), requestId);
                    sendError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                            "Internal service credentials not authorized for user-facing endpoint: gateway assertion required", request.getRequestURI());
                    return;
                }

                UserSecurityContextHolder.setContext(UserSecurityContext.internal());
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Check for Gateway Assertion
            String assertionHeader = request.getHeader(HEADER_GATEWAY_ASSERTION);
            if (assertionHeader != null && !assertionHeader.isBlank()) {
                GatewayAssertion assertion;
                try {
                    assertion = gatewayAssertionVerifier.verify(assertionHeader, requestId, request.getMethod(), request.getRequestURI());
                } catch (GatewayAssertionException e) {
                    securityAuditLogger.logAssertionRejected(e.getMessage(), request.getRequestURI(), requestId);
                    sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            "Invalid or expired gateway assertion: " + e.getMessage(), request.getRequestURI());
                    return;
                }

                // Anti-spoofing verification: if raw X-User-Id is also passed, it must match assertion userId
                String rawUserId = request.getHeader(HEADER_USER_ID);
                if (rawUserId != null && !rawUserId.isBlank()) {
                    try {
                        UUID parsedRawUserId = UUID.fromString(rawUserId.trim());
                        if (!assertion.userId().equals(parsedRawUserId)) {
                            securityAuditLogger.logAssertionRejected("Gateway assertion user ID does not match X-User-Id header", request.getRequestURI(), requestId);
                            sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                    "Gateway assertion user ID does not match X-User-Id header", request.getRequestURI());
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        securityAuditLogger.logAssertionRejected("Malformed X-User-Id header format", request.getRequestURI(), requestId);
                        sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                "Malformed X-User-Id header format", request.getRequestURI());
                        return;
                    }
                }

                UserSecurityContext context = UserSecurityContext.user(assertion.userId(), assertion.roles());
                UserSecurityContextHolder.setContext(context);

                // Wrap request to provide verified user ID and roles downstream, preventing any reliance on untrusted raw values
                HttpServletRequest wrappedRequest = new AuthenticatedRequestWrapper(
                        request,
                        assertion.userId().toString(),
                        String.join(",", assertion.roles())
                );

                filterChain.doFilter(wrappedRequest, response);
                return;
            }

            // 3. Reject direct untrusted client identity headers (identity spoofing mitigation)
            String rawUserId = request.getHeader(HEADER_USER_ID);
            if (rawUserId != null && !rawUserId.isBlank()) {
                securityAuditLogger.logAssertionRejected("Direct client identity header rejected: gateway assertion required", request.getRequestURI(), requestId);
                sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Direct client identity headers rejected: gateway assertion required", request.getRequestURI());
                return;
            }

            // 4. Missing credentials
            securityAuditLogger.logAuthenticationFailure("Missing user identity or internal service credentials", request.getRequestURI(), requestId);
            sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Authentication required: missing user identity or internal service credentials",
                    request.getRequestURI());
        } finally {
            UserSecurityContextHolder.clear();
        }
    }

    private boolean isAuthorizedInternalEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        String method = request.getMethod();

        // 1. GET /api/v1/documents/{id}
        if (path.matches("^/api/v1/documents/[a-fA-F0-9\\-]+$") && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        // 2. GET /api/v1/documents/{id}/content
        if (path.matches("^/api/v1/documents/[a-fA-F0-9\\-]+/content$") && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        // 3. GET /api/v1/documents/{id}/permissions/verify
        if (path.matches("^/api/v1/documents/[a-fA-F0-9\\-]+/permissions/verify$") && "GET".equalsIgnoreCase(method)) {
            return true;
        }
        // 4. POST /api/v1/documents/batch/status
        if (path.equals("/api/v1/documents/batch/status") && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        // 5. PATCH /api/v1/documents/{id}/processing-status/{status}
        if (path.matches("^/api/v1/documents/[a-fA-F0-9\\-]+/processing-status/[a-zA-Z0-9_]+$") && "PATCH".equalsIgnoreCase(method)) {
            return true;
        }
        return false;
    }

    private void sendError(
            HttpServletResponse response,
            HttpStatus status,
            String error,
            String message,
            String path
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .build();
        response.getOutputStream().write(objectMapper.writeValueAsBytes(errorDto));
    }

    private static class AuthenticatedRequestWrapper extends HttpServletRequestWrapper {
        private final String userId;
        private final String roles;

        public AuthenticatedRequestWrapper(HttpServletRequest request, String userId, String roles) {
            super(request);
            this.userId = userId;
            this.roles = roles;
        }

        @Override
        public String getHeader(String name) {
            if (HEADER_USER_ID.equalsIgnoreCase(name)) {
                return userId;
            }
            if (HEADER_USER_ROLES.equalsIgnoreCase(name) && roles != null) {
                return roles;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HEADER_USER_ID.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(userId));
            }
            if (HEADER_USER_ROLES.equalsIgnoreCase(name) && roles != null) {
                return Collections.enumeration(List.of(roles));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new HashSet<>();
            Enumeration<String> parentNames = super.getHeaderNames();
            if (parentNames != null) {
                while (parentNames.hasMoreElements()) {
                    names.add(parentNames.nextElement());
                }
            }
            names.add(HEADER_USER_ID);
            if (roles != null) {
                names.add(HEADER_USER_ROLES);
            }
            return Collections.enumeration(names);
        }
    }
}
