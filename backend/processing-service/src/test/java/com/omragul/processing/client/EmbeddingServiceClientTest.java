package com.omragul.processing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.client.dto.BatchEmbeddingResponseDto;
import com.omragul.processing.client.impl.EmbeddingServiceClientImpl;
import com.omragul.processing.exception.DownstreamServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EmbeddingServiceClientTest {

    private EmbeddingServiceClientImpl client;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = new EmbeddingServiceClientImpl("http://localhost:8003", 2000, 5000);

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8003");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        Field field = EmbeddingServiceClientImpl.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, restClient);
    }

    @Test
    @DisplayName("Should successfully generate batch embeddings via POST /api/v1/embeddings/batch")
    void testGenerateBatchEmbeddingsSuccess() throws Exception {
        List<String> texts = List.of("Hello world", "Machine learning");

        BatchEmbeddingResponseDto mockResponse = BatchEmbeddingResponseDto.builder()
                .model("all-MiniLM-L6-v2")
                .dimension(384)
                .embeddings(List.of(
                        List.of(0.012, -0.045, 0.089),
                        List.of(0.055, -0.021, 0.011)
                ))
                .build();

        mockServer.expect(requestTo("http://localhost:8003/api/v1/embeddings/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.texts[0]").value("Hello world"))
                .andExpect(jsonPath("$.texts[1]").value("Machine learning"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mockResponse), MediaType.APPLICATION_JSON));

        BatchEmbeddingResponseDto response = client.generateBatchEmbeddings(texts);

        assertNotNull(response);
        assertEquals("all-MiniLM-L6-v2", response.getModel());
        assertEquals(384, response.getDimension());
        assertEquals(2, response.getEmbeddings().size());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle empty texts list gracefully without HTTP request")
    void testGenerateBatchEmbeddingsEmptyList() {
        BatchEmbeddingResponseDto response = client.generateBatchEmbeddings(List.of());
        assertNotNull(response);
        assertNull(response.getEmbeddings());
    }

    @Test
    @DisplayName("Should handle null texts list gracefully without HTTP request")
    void testGenerateBatchEmbeddingsNullList() {
        BatchEmbeddingResponseDto response = client.generateBatchEmbeddings(null);
        assertNotNull(response);
        assertNull(response.getEmbeddings());
    }

    @Test
    @DisplayName("Should propagate 4xx client errors as DownstreamServiceException with status code")
    void testGenerateBatchEmbeddingsClientError4xx() {
        mockServer.expect(requestTo("http://localhost:8003/api/v1/embeddings/batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"detail\":\"Texts cannot be empty\"}"));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class,
                () -> client.generateBatchEmbeddings(List.of("Sample text")));

        assertEquals("embedding-service", ex.getServiceName());
        assertEquals(400, ex.getStatusCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should propagate 5xx server errors as DownstreamServiceException with status code")
    void testGenerateBatchEmbeddingsServerError5xx() {
        mockServer.expect(requestTo("http://localhost:8003/api/v1/embeddings/batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("{\"detail\":\"Model inference failed\"}"));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class,
                () -> client.generateBatchEmbeddings(List.of("Sample text")));

        assertEquals("embedding-service", ex.getServiceName());
        assertEquals(502, ex.getStatusCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle network connection failures and timeouts")
    void testGenerateBatchEmbeddingsNetworkFailure() {
        mockServer.expect(requestTo("http://localhost:8003/api/v1/embeddings/batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class,
                () -> client.generateBatchEmbeddings(List.of("Sample text")));

        assertEquals("embedding-service", ex.getServiceName());
        assertTrue(ex.getMessage().contains("Network failure connecting to Embedding Service") || ex.getMessage().contains("Failed to communicate"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle malformed response payload from Embedding Service")
    void testGenerateBatchEmbeddingsMalformedJson() {
        mockServer.expect(requestTo("http://localhost:8003/api/v1/embeddings/batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not-valid-json-response", MediaType.APPLICATION_JSON));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class,
                () -> client.generateBatchEmbeddings(List.of("Sample text")));

        assertEquals("embedding-service", ex.getServiceName());
        mockServer.verify();
    }
}
