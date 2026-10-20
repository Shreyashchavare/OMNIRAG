package com.omragul.processing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.client.dto.RagIndexResponseDto;
import com.omragul.processing.client.impl.RagServiceClientImpl;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RagServiceClientTest {

    private RagServiceClientImpl client;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = new RagServiceClientImpl("http://localhost:8002", 2000, 5000);

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8002");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        Field field = RagServiceClientImpl.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, restClient);
    }

    @Test
    @DisplayName("Should successfully index chunks via POST /api/v1/rag/index")
    void testIndexChunksSuccess() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        List<String> chunks = List.of("First chunk content", "Second chunk content");

        RagIndexResponseDto mockResponse = RagIndexResponseDto.builder()
                .indexed(2)
                .build();

        mockServer.expect(requestTo("http://localhost:8002/api/v1/rag/index"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.chunks[0].text").value("First chunk content"))
                .andExpect(jsonPath("$.chunks[0].document_id").value(docId.toString()))
                .andExpect(jsonPath("$.chunks[0].chunk_index").value(0))
                .andExpect(jsonPath("$.chunks[0].knowledge_base_id").value(kbId.toString()))
                .andExpect(jsonPath("$.chunks[1].text").value("Second chunk content"))
                .andExpect(jsonPath("$.chunks[1].document_id").value(docId.toString()))
                .andExpect(jsonPath("$.chunks[1].chunk_index").value(1))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mockResponse), MediaType.APPLICATION_JSON));

        int indexedCount = client.indexChunks(docId, kbId, chunks);

        assertEquals(2, indexedCount);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle empty chunks list without sending HTTP request")
    void testIndexChunksEmpty() {
        int result = client.indexChunks(UUID.randomUUID(), UUID.randomUUID(), List.of());
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Should handle null chunks list without sending HTTP request")
    void testIndexChunksNull() {
        int result = client.indexChunks(UUID.randomUUID(), null, null);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Should index chunks without knowledgeBaseId when null")
    void testIndexChunksWithoutKnowledgeBaseId() throws Exception {
        UUID docId = UUID.randomUUID();
        List<String> chunks = List.of("Standalone chunk");

        RagIndexResponseDto mockResponse = RagIndexResponseDto.builder()
                .indexed(1)
                .build();

        mockServer.expect(requestTo("http://localhost:8002/api/v1/rag/index"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.chunks[0].text").value("Standalone chunk"))
                .andExpect(jsonPath("$.chunks[0].document_id").value(docId.toString()))
                .andExpect(jsonPath("$.chunks[0].chunk_index").value(0))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mockResponse), MediaType.APPLICATION_JSON));

        int count = client.indexChunks(docId, null, chunks);
        assertEquals(1, count);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should propagate 4xx error from RAG service as DownstreamServiceException with status code")
    void testIndexChunksClientError4xx() {
        UUID docId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8002/api/v1/rag/index"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"detail\":\"Chunks cannot be empty\"}"));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class,
                () -> client.indexChunks(docId, null, List.of("Some text")));

        assertEquals("rag-service", ex.getServiceName());
        assertEquals(400, ex.getStatusCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should propagate 5xx server error from RAG service as DownstreamServiceException")
    void testIndexChunksServerError5xx() {
        UUID docId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8002/api/v1/rag/index"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"detail\":\"Vector store write failure\"}"));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class,
                () -> client.indexChunks(docId, null, List.of("Some text")));

        assertEquals("rag-service", ex.getServiceName());
        assertEquals(500, ex.getStatusCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle network connection failures to RAG service")
    void testIndexChunksNetworkFailure() {
        UUID docId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8002/api/v1/rag/index"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class,
                () -> client.indexChunks(docId, null, List.of("Some text")));

        assertEquals("rag-service", ex.getServiceName());
        assertTrue(ex.getMessage().contains("Network failure connecting to RAG Service") || ex.getMessage().contains("Failed to communicate"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle malformed response payload from RAG Service")
    void testIndexChunksMalformedJson() {
        UUID docId = UUID.randomUUID();
        mockServer.expect(requestTo("http://localhost:8002/api/v1/rag/index"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("corrupted-not-json", MediaType.APPLICATION_JSON));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class,
                () -> client.indexChunks(docId, null, List.of("Some text")));

        assertEquals("rag-service", ex.getServiceName());
        mockServer.verify();
    }
}
