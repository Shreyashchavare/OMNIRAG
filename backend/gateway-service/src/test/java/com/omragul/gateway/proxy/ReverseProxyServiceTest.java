package com.omragul.gateway.proxy;

import com.omragul.gateway.config.GatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

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
}
