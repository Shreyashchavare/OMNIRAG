package com.omragul.processing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.client.impl.KnowledgeServiceClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KnowledgeServiceClientTest {

    private KnowledgeServiceClientImpl client;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = new KnowledgeServiceClientImpl("http://localhost:8082", 2000, 5000);

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        Field field = KnowledgeServiceClientImpl.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, restClient);
    }

    @Test
    @DisplayName("Should return DocumentDto when document is found")
    void testGetDocumentFound() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("test.pdf")
                .mimeType("application/pdf")
                .status("ACTIVE")
                .build();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(doc), MediaType.APPLICATION_JSON));

        Optional<DocumentDto> result = client.getDocument(docId);
        assertTrue(result.isPresent());
        assertEquals(docId, result.get().getId());
        assertEquals("test.pdf", result.get().getName());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return Optional.empty() when document is not found (404)")
    void testGetDocumentNotFound() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<DocumentDto> result = client.getDocument(docId);
        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should update document processing status via PATCH")
    void testUpdateProcessingStatus() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/processing-status/COMPLETED"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> client.updateDocumentProcessingStatus(docId, "COMPLETED"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw DownstreamServiceException on Knowledge Service 5xx server error")
    void testGetDocumentServerError5xx() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        com.omragul.processing.exception.DownstreamServiceException ex = assertThrows(
                com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId)
        );
        assertEquals("knowledge-service", ex.getServiceName());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw DownstreamServiceException on network/connection timeout")
    void testGetDocumentNetworkTimeout() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));

        com.omragul.processing.exception.DownstreamServiceException ex = assertThrows(
                com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId)
        );
        assertEquals("knowledge-service", ex.getServiceName());
        mockServer.verify();
    }
}
