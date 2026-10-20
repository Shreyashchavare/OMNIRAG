package com.omragul.gateway.controller;

import com.omragul.gateway.config.GatewayProperties;
import com.omragul.gateway.config.SecurityConfig;
import com.omragul.gateway.proxy.ReverseProxyService;
import com.omragul.gateway.security.JwtAuthenticationEntryPoint;
import com.omragul.gateway.security.JwtAuthenticationFilter;
import com.omragul.gateway.security.JwtValidator;
import com.omragul.gateway.util.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GatewayRouteController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtValidator.class,
        GatewayProperties.class
})
class GatewayRouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReverseProxyService reverseProxyService;

    @Test
    @DisplayName("GET /health is public and returns 200 UP")
    void testHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("gateway-service"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login is public and proxies to identity-service without token")
    void testAuthLoginPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test\",\"password\":\"pass\"}"))
                .andExpect(status().isOk());

        verify(reverseProxyService).forward(
                eq("identity-service"),
                eq("http://localhost:8081"),
                eq("/api/v1/auth/login"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("POST /api/v1/auth/signup is public and proxies to identity-service without token")
    void testAuthSignupPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test\",\"email\":\"test@example.com\",\"password\":\"pass\"}"))
                .andExpect(status().isOk());

        verify(reverseProxyService).forward(
                eq("identity-service"),
                eq("http://localhost:8081"),
                eq("/api/v1/auth/signup"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Protected route /api/v1/documents returns 401 when no token is provided")
    void testProtectedWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/documents"));
    }

    @Test
    @DisplayName("Protected route /api/v1/documents returns 401 with expired token")
    void testProtectedWithExpiredTokenReturns401() throws Exception {
        String expiredToken = TestJwtFactory.generateExpiredToken(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Protected route /api/v1/documents proxies to knowledge-service with valid JWT")
    void testProtectedKnowledgeRouteWithValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = TestJwtFactory.generateValidToken(userId, List.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(reverseProxyService).forward(
                eq("knowledge-service"),
                eq("http://localhost:8082"),
                eq("/api/v1/documents"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Route /api/v1/knowledge-bases rewrites path to /knowledge-bases for knowledge-service")
    void testKnowledgeBaseRewriteRoute() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = TestJwtFactory.generateValidToken(userId, List.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/knowledge-bases/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(reverseProxyService).forward(
                eq("knowledge-service"),
                eq("http://localhost:8082"),
                eq("/knowledge-bases/123"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Protected route /api/v1/processing/jobs proxies to processing-service with valid JWT")
    void testProcessingRouteWithValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = TestJwtFactory.generateValidToken(userId, List.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/processing/jobs/456")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(reverseProxyService).forward(
                eq("processing-service"),
                eq("http://localhost:8083"),
                eq("/api/v1/processing/jobs/456"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Protected route /api/analytics/dashboard proxies to analytics-service with valid JWT")
    void testAnalyticsRouteWithValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = TestJwtFactory.generateValidToken(userId, List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/analytics/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(reverseProxyService).forward(
                eq("analytics-service"),
                eq("http://localhost:8084"),
                eq("/api/analytics/dashboard"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Protected route /api/notifications proxies to notification-service with valid JWT")
    void testNotificationRouteWithValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = TestJwtFactory.generateValidToken(userId, List.of("ROLE_USER"));

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk());

        verify(reverseProxyService).forward(
                eq("notification-service"),
                eq("http://localhost:8085"),
                eq("/api/notifications"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("CORS preflight request receives appropriate headers")
    void testCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
