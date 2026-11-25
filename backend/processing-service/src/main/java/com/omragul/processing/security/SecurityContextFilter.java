package com.omragul.processing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.dto.response.ErrorResponseDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
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
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/processing");
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
                if (internalAuthService.isAuthorizedInternalService(secretHeader)) {
                    UserSecurityContextHolder.setContext(UserSecurityContext.internal());
                    filterChain.doFilter(request, response);
                    return;
                } else {
                    securityAuditLogger.logAuthenticationFailure("Invalid internal service credentials", request.getRequestURI(), requestId);
                    sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            "Invalid internal service credentials", request.getRequestURI());
                    return;
                }
            }

            // 2. Check for Gateway Assertion
            String assertionHeader = request.getHeader(HEADER_GATEWAY_ASSERTION);
            if (assertionHeader != null && !assertionHeader.isBlank()) {
                try {
                    GatewayAssertion assertion = gatewayAssertionVerifier.verify(assertionHeader, requestId, request.getMethod(), request.getRequestURI());
                    UserSecurityContext context = UserSecurityContext.user(assertion.userId(), assertion.roles());
                    UserSecurityContextHolder.setContext(context);
                    filterChain.doFilter(request, response);
                    return;
                } catch (GatewayAssertionException e) {
                    securityAuditLogger.logAssertionRejected(e.getMessage(), request.getRequestURI(), requestId);
                    sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            "Invalid or expired gateway assertion", request.getRequestURI());
                    return;
                }
            }

            // 3. Reject direct untrusted client identity headers
            String rawUserId = request.getHeader(HEADER_USER_ID);
            if (rawUserId != null && !rawUserId.isBlank()) {
                securityAuditLogger.logAssertionRejected("Direct client identity header rejected: gateway assertion required", request.getRequestURI(), requestId);
                sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Direct client identity headers rejected: gateway assertion required", request.getRequestURI());
                return;
            }

            // 4. Missing credentials
            sendError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Authentication required: missing user identity or internal service credentials",
                    request.getRequestURI());
        } finally {
            UserSecurityContextHolder.clear();
        }
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
}
