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
        client = new KnowledgeServiceClientImpl("http://localhost:8082", "test-secret", 2000, 5000);

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
                .andExpect(header("X-Internal-Secret", "test-secret"))
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
                .andExpect(header("X-Internal-Secret", "test-secret"))
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

    @Test
    @DisplayName("Should throw DownstreamServiceException on Knowledge Service 403 Forbidden (invalid internal secret)")
    void testGetDocumentForbidden403() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Secret", "test-secret"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        com.omragul.processing.exception.DownstreamServiceException ex = assertThrows(
                com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId)
        );
        assertEquals("knowledge-service", ex.getServiceName());
        assertEquals(403, ex.getStatusCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return true when Knowledge Service reports authorized=true for write access")
    void testVerifyDocumentWriteAccessAuthorized() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String jsonResponse = "{\"documentId\":\"" + docId + "\",\"userId\":\"" + userId + "\",\"permission\":\"WRITE\",\"authorized\":true}";

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/permissions/verify?userId=" + userId + "&permission=WRITE"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Secret", "test-secret"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        boolean result = client.verifyDocumentWriteAccess(docId, userId);
        assertTrue(result);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return false when Knowledge Service reports authorized=false for write access")
    void testVerifyDocumentWriteAccessUnauthorized() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String jsonResponse = "{\"documentId\":\"" + docId + "\",\"userId\":\"" + userId + "\",\"permission\":\"WRITE\",\"authorized\":false}";

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/permissions/verify?userId=" + userId + "&permission=WRITE"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Secret", "test-secret"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        boolean result = client.verifyDocumentWriteAccess(docId, userId);
        assertFalse(result);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw DocumentNotFoundException when Knowledge Service returns 404 for write access verification")
    void testVerifyDocumentWriteAccessNotFound404() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/permissions/verify?userId=" + userId + "&permission=WRITE"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Secret", "test-secret"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(com.omragul.processing.exception.DocumentNotFoundException.class, () ->
                client.verifyDocumentWriteAccess(docId, userId));
        mockServer.verify();
    }

    // =========================================================
    // PROC-02: ROBUST ERROR & 404 HANDLING TESTS
    // =========================================================

    @Test
    @DisplayName("PROC-02: Should return Optional.empty() when Knowledge Service returns 404 with empty body")
    void testGetDocumentNotFoundEmptyBody() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body(new byte[0]));

        Optional<DocumentDto> result = client.getDocument(docId);
        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-02: Should return Optional.empty() when Knowledge Service returns 404 with HTML / non-JSON body")
    void testGetDocumentNotFoundHtmlBody() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><body>404 Not Found</body></html>"));

        Optional<DocumentDto> result = client.getDocument(docId);
        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-02: Should throw DownstreamServiceException when Knowledge Service returns 5xx with HTML / non-JSON error body")
    void testGetDocumentServerErrorHtmlBody() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><body>503 Service Unavailable</body></html>"));

        com.omragul.processing.exception.DownstreamServiceException ex = assertThrows(
                com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId)
        );
        assertEquals("knowledge-service", ex.getServiceName());
        assertEquals(503, ex.getStatusCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-02: Should throw DocumentNotFoundException when verify permissions returns 404 with HTML / non-JSON body")
    void testVerifyWriteAccessNotFoundHtmlBody() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/permissions/verify?userId=" + userId + "&permission=WRITE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><body>Document Not Found</body></html>"));

        assertThrows(com.omragul.processing.exception.DocumentNotFoundException.class, () ->
                client.verifyDocumentWriteAccess(docId, userId));
        mockServer.verify();
    }

    // =========================================================
    // PROC-04: DOCUMENT CONTENT STREAMING TESTS
    // =========================================================

    @Test
    @DisplayName("PROC-04: Should stream document content and preserve MIME type and filename")
    void testStreamDocumentContentSuccess() {
        UUID docId = UUID.randomUUID();
        String expectedData = "Mock Binary File Stream Content From Knowledge Service";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"");
        headers.setContentLength(expectedData.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/content"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Secret", "test-secret"))
                .andRespond(withSuccess(expectedData, MediaType.APPLICATION_PDF).headers(headers));

        String streamedResult = client.streamDocumentContent(docId, (inputStream, contentType, filename, contentLength) -> {
            assertEquals("application/pdf", contentType);
            assertEquals("report.pdf", filename);
            assertEquals(expectedData.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, contentLength);
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        });

        assertEquals(expectedData, streamedResult);
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-04: Should throw DocumentNotFoundException when document content returns 404")
    void testStreamDocumentContentNotFound() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/content"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("Document storage file not found"));

        assertThrows(com.omragul.processing.exception.DocumentNotFoundException.class, () ->
                client.streamDocumentContent(docId, (inputStream, contentType, filename, contentLength) -> null));
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-04: Should throw DownstreamServiceException when document content returns 403 Forbidden")
    void testStreamDocumentContentForbidden() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/content"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        com.omragul.processing.exception.DownstreamServiceException ex = assertThrows(
                com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.streamDocumentContent(docId, (inputStream, contentType, filename, contentLength) -> null)
        );
        assertEquals("knowledge-service", ex.getServiceName());
        assertEquals(403, ex.getStatusCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-04: Should throw DownstreamServiceException when document content returns 500 Server Error")
    void testStreamDocumentContentServerError() {
        UUID docId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/content"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        com.omragul.processing.exception.DownstreamServiceException ex = assertThrows(
                com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.streamDocumentContent(docId, (inputStream, contentType, filename, contentLength) -> null)
        );
        assertEquals("knowledge-service", ex.getServiceName());
        assertEquals(500, ex.getStatusCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-04: Should throw PermanentProcessingException when document file size exceeds maximum limit")
    void testStreamDocumentContentOversized() {
        UUID docId = UUID.randomUUID();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentLength(200_000_000L); // 200 MB exceeds 100 MB limit

        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/content"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("small body", MediaType.APPLICATION_OCTET_STREAM).headers(headers));

        com.omragul.processing.exception.PermanentProcessingException ex = assertThrows(
                com.omragul.processing.exception.PermanentProcessingException.class,
                () -> client.streamDocumentContent(docId, (inputStream, contentType, filename, contentLength) -> null)
        );
        assertEquals("FILE_TOO_LARGE", ex.getErrorCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("FINDING-3.4-01: Missing Content-Length header with oversized body throws FILE_TOO_LARGE")
    void testStreamDocumentContentMissingContentLengthOversized() throws Exception {
        UUID docId = UUID.randomUUID();
        // Client configured with 50-byte max limit
        KnowledgeServiceClientImpl boundedClient = new KnowledgeServiceClientImpl(
                "http://localhost:8082", "test-secret", 2000, 5000, 50L, objectMapper
        );
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        MockRestServiceServer localMockServer = MockRestServiceServer.bindTo(builder).build();
        Field field = KnowledgeServiceClientImpl.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(boundedClient, builder.build());

        // Body with 100 bytes (no Content-Length header)
        byte[] oversizedBody = new byte[100];
        localMockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/content"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(oversizedBody, MediaType.APPLICATION_OCTET_STREAM));

        com.omragul.processing.exception.PermanentProcessingException ex = assertThrows(
                com.omragul.processing.exception.PermanentProcessingException.class,
                () -> boundedClient.streamDocumentContent(docId, (inputStream, contentType, filename, contentLength) -> {
                    return inputStream.readAllBytes();
                })
        );
        assertEquals("FILE_TOO_LARGE", ex.getErrorCode());
        localMockServer.verify();
    }

    @Test
    @DisplayName("FINDING-3.4-01: Deceptively small Content-Length header with oversized body throws FILE_TOO_LARGE")
    void testStreamDocumentContentDeceptiveContentLengthOversized() throws Exception {
        UUID docId = UUID.randomUUID();
        KnowledgeServiceClientImpl boundedClient = new KnowledgeServiceClientImpl(
                "http://localhost:8082", "test-secret", 2000, 5000, 50L, objectMapper
        );
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        MockRestServiceServer localMockServer = MockRestServiceServer.bindTo(builder).build();
        Field field = KnowledgeServiceClientImpl.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(boundedClient, builder.build());

        // Header claims 20 bytes, but body sends 80 bytes
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentLength(20L);

        byte[] oversizedBody = new byte[80];
        localMockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId + "/content"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(oversizedBody, MediaType.APPLICATION_OCTET_STREAM).headers(headers));

        com.omragul.processing.exception.PermanentProcessingException ex = assertThrows(
                com.omragul.processing.exception.PermanentProcessingException.class,
                () -> boundedClient.streamDocumentContent(docId, (inputStream, contentType, filename, contentLength) -> {
                    return inputStream.readAllBytes();
                })
        );
        assertEquals("FILE_TOO_LARGE", ex.getErrorCode());
        localMockServer.verify();
    }

    @Test
    @DisplayName("FINDING-3.4-04: Client errors (400, 401, 403, 422) are classified as non-retryable")
    void testDownstream4xxClassificationPermanent() {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        UUID docId3 = UUID.randomUUID();
        UUID docId4 = UUID.randomUUID();

        // Declare all 4 expectations before issuing requests
        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId1))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId2))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId3))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId4))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        var ex400 = assertThrows(com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId1));
        assertFalse(ex400.isRetryable(), "400 Bad Request must not be retryable");

        var ex401 = assertThrows(com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId2));
        assertFalse(ex401.isRetryable(), "401 Unauthorized must not be retryable");

        var ex403 = assertThrows(com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId3));
        assertFalse(ex403.isRetryable(), "403 Forbidden must not be retryable");

        var ex422 = assertThrows(com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId4));
        assertFalse(ex422.isRetryable(), "422 Unprocessable Entity must not be retryable");

        mockServer.verify();
    }

    @Test
    @DisplayName("FINDING-3.4-04: Transient client errors (408 Request Timeout, 429 Too Many Requests) are classified as retryable")
    void testDownstreamTransientClientErrorsRetryable() {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();

        // Declare both expectations before issuing requests
        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId1))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT));
        mockServer.expect(requestTo("http://localhost:8082/api/v1/documents/" + docId2))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        var ex408 = assertThrows(com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId1));
        assertTrue(ex408.isRetryable(), "408 Request Timeout must be retryable");

        var ex429 = assertThrows(com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.getDocument(docId2));
        assertTrue(ex429.isRetryable(), "429 Too Many Requests must be retryable");

        mockServer.verify();
    }
}
