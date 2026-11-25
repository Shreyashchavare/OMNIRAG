package com.omragul.gateway.proxy;

import com.omragul.gateway.config.GatewayProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ReverseProxyServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Forward to non-existent downstream service returns 502 Bad Gateway")
    void testForwardServiceUnavailable() throws Exception {
        GatewayProperties properties = new GatewayProperties();
        properties.getHttp().setConnectTimeoutMs(500);
        properties.getHttp().setReadTimeoutMs(1000);

        ReverseProxyService proxyService = new ReverseProxyService(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Forward to a non-listening port on localhost
        proxyService.forward("knowledge-service", "http://127.0.0.1:59999", "/api/v1/documents", request, response);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).contains("BAD_GATEWAY");
        assertThat(response.getContentAsString()).contains("Downstream service unavailable: knowledge-service");
    }

    @Test
    @DisplayName("Forward with invalid URI returns 400 Bad Request")
    void testForwardInvalidUri() throws Exception {
        GatewayProperties properties = new GatewayProperties();
        ReverseProxyService proxyService = new ReverseProxyService(properties, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        proxyService.forward("knowledge-service", "http://[invalid-host", "/path", request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("BAD_REQUEST");
    }

    @Test
    @DisplayName("Forward strips hop-by-hop internal credentials (x-internal-secret, x-internal-service-secret, and x-internal-token)")
    void testForwardStripsInternalHeaders() throws Exception {
        AtomicBoolean internalSecretReceived = new AtomicBoolean(false);
        AtomicBoolean internalServiceSecretReceived = new AtomicBoolean(false);
        AtomicBoolean internalTokenReceived = new AtomicBoolean(false);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/test", exchange -> {
            if (exchange.getRequestHeaders().containsKey("x-internal-secret")) {
                internalSecretReceived.set(true);
            }
            if (exchange.getRequestHeaders().containsKey("x-internal-service-secret")) {
                internalServiceSecretReceived.set(true);
            }
            if (exchange.getRequestHeaders().containsKey("x-internal-token")) {
                internalTokenReceived.set(true);
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            GatewayProperties properties = new GatewayProperties();
            properties.getAssertion().setEnabled(false);
            ReverseProxyService proxyService = new ReverseProxyService(properties, objectMapper);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
            request.addHeader("x-internal-secret", "forged-secret");
            request.addHeader("x-internal-service-secret", "forged-service-secret");
            request.addHeader("x-internal-token", "forged-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            proxyService.forward("test-service", "http://127.0.0.1:" + port, "/test", request, response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(internalSecretReceived.get()).as("x-internal-secret must be stripped by proxy").isFalse();
            assertThat(internalServiceSecretReceived.get()).as("x-internal-service-secret must be stripped by proxy").isFalse();
            assertThat(internalTokenReceived.get()).as("x-internal-token must be stripped by proxy").isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Forward strips untrusted client-supplied identity headers (x-user-id, x-user-roles, x-user-email, x-user-name)")
    void testForwardStripsUntrustedIdentityHeaders() throws Exception {
        AtomicBoolean userIdReceived = new AtomicBoolean(false);
        AtomicBoolean userRolesReceived = new AtomicBoolean(false);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/untrusted-identity", exchange -> {
            if (exchange.getRequestHeaders().containsKey("x-user-id")) {
                userIdReceived.set(true);
            }
            if (exchange.getRequestHeaders().containsKey("x-user-roles")) {
                userRolesReceived.set(true);
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            GatewayProperties properties = new GatewayProperties();
            properties.getAssertion().setEnabled(false);
            ReverseProxyService proxyService = new ReverseProxyService(properties, objectMapper);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/untrusted-identity");
            request.addHeader("x-user-id", UUID.randomUUID().toString());
            request.addHeader("x-user-roles", "ROLE_ADMIN");
            MockHttpServletResponse response = new MockHttpServletResponse();

            proxyService.forward("test-service", "http://127.0.0.1:" + port, "/untrusted-identity", request, response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(userIdReceived.get()).as("Unverified client x-user-id header must be stripped").isFalse();
            assertThat(userRolesReceived.get()).as("Unverified client x-user-roles header must be stripped").isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Forward sets verified identity headers and signed Gateway assertion from verified JWT attributes")
    void testForwardPropagatesVerifiedIdentityHeaders() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> forwardedUserId = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> forwardedRoles = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> forwardedAssertion = new java.util.concurrent.atomic.AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/verified-identity", exchange -> {
            forwardedUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            forwardedRoles.set(exchange.getRequestHeaders().getFirst("X-User-Roles"));
            forwardedAssertion.set(exchange.getRequestHeaders().getFirst("X-Gateway-Assertion"));
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            GatewayProperties properties = new GatewayProperties();
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            java.security.KeyPair keyPair = kpg.generateKeyPair();
            com.omragul.gateway.security.GatewayAssertionService assertionService =
                    new com.omragul.gateway.security.GatewayAssertionService(keyPair.getPrivate());
            ReverseProxyService proxyService = new ReverseProxyService(properties, objectMapper, assertionService);

            UUID verifiedId = UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/verified-identity");
            request.setAttribute(com.omragul.gateway.security.JwtAuthenticationFilter.ATTR_USER_ID, verifiedId);
            request.setAttribute(com.omragul.gateway.security.JwtAuthenticationFilter.ATTR_USER_ROLES, "ROLE_USER");
            MockHttpServletResponse response = new MockHttpServletResponse();

            proxyService.forward("test-service", "http://127.0.0.1:" + port, "/verified-identity", request, response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(forwardedUserId.get()).isEqualTo(verifiedId.toString());
            assertThat(forwardedRoles.get()).isEqualTo("ROLE_USER");
            assertThat(forwardedAssertion.get()).as("X-Gateway-Assertion must be generated and forwarded").isNotNull();
            String[] assertionParts = forwardedAssertion.get().split("\\.");
            assertThat(assertionParts).hasSize(3);

            io.jsonwebtoken.Claims assertionClaims = io.jsonwebtoken.Jwts.parser()
                    .verifyWith(keyPair.getPublic())
                    .requireIssuer("gateway-service")
                    .build()
                    .parseSignedClaims(forwardedAssertion.get())
                    .getPayload();
            assertThat(assertionClaims.getSubject()).isEqualTo(verifiedId.toString());
            assertThat(assertionClaims.get("roles", String.class)).isEqualTo("ROLE_USER");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Forward strips untrusted client-supplied x-gateway-assertion")
    void testForwardStripsClientSuppliedGatewayAssertion() throws Exception {
        AtomicBoolean assertionReceived = new AtomicBoolean(false);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/strip-assertion", exchange -> {
            if (exchange.getRequestHeaders().containsKey("x-gateway-assertion")) {
                assertionReceived.set(true);
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            GatewayProperties properties = new GatewayProperties();
            properties.getAssertion().setEnabled(false);
            ReverseProxyService proxyService = new ReverseProxyService(properties, objectMapper);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/strip-assertion");
            request.addHeader("x-gateway-assertion", "forged.gateway.assertion");
            MockHttpServletResponse response = new MockHttpServletResponse();

            proxyService.forward("test-service", "http://127.0.0.1:" + port, "/strip-assertion", request, response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(assertionReceived.get()).as("Client-supplied x-gateway-assertion must be stripped").isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Forward aborts with 500 when assertion generation returns null for authenticated user")
    void testForwardAbortsWhenAssertionReturnsNull() throws Exception {
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/authenticated-endpoint", exchange -> {
            downstreamCalled.set(true);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            GatewayProperties properties = new GatewayProperties();
            // Assertion is disabled, so createAssertion returns null
            properties.getAssertion().setEnabled(false);
            ReverseProxyService proxyService = new ReverseProxyService(properties, objectMapper);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/authenticated-endpoint");
            UUID verifiedId = UUID.randomUUID();
            request.setAttribute(com.omragul.gateway.security.JwtAuthenticationFilter.ATTR_USER_ID, verifiedId);
            request.setAttribute(com.omragul.gateway.security.JwtAuthenticationFilter.ATTR_USER_ROLES, "ROLE_USER");
            MockHttpServletResponse response = new MockHttpServletResponse();

            proxyService.forward("test-service", "http://127.0.0.1:" + port, "/authenticated-endpoint", request, response);

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString()).contains("INTERNAL_SERVER_ERROR");
            assertThat(response.getContentAsString()).contains("Failed to generate downstream authorization assertion");
            assertThat(downstreamCalled.get()).as("Downstream service must NOT be called when assertion generation fails").isFalse();
        } finally {
            server.stop(0);
        }
    }
}
