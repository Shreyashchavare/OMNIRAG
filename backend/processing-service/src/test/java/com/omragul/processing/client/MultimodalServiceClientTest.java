package com.omragul.processing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.client.dto.ChunkResponseDto;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.client.dto.OcrResponseDto;
import com.omragul.processing.client.dto.PdfResponseDto;
import com.omragul.processing.client.dto.SpeechResponseDto;
import com.omragul.processing.client.impl.MultimodalServiceClientImpl;
import com.omragul.processing.exception.DocumentNotFoundException;
import com.omragul.processing.exception.DownstreamServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MultimodalServiceClientTest {

    private MultimodalServiceClientImpl client;
    private KnowledgeServiceClient knowledgeServiceClient;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        knowledgeServiceClient = mock(KnowledgeServiceClient.class);
        client = new MultimodalServiceClientImpl(knowledgeServiceClient, "http://localhost:8003", 2000, 5000, "test-internal-secret");

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8003");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        Field field = MultimodalServiceClientImpl.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, restClient);
    }

    private void stubStreamContent(UUID docId, String content, String mimeType, String filename) {
        when(knowledgeServiceClient.streamDocumentContent(eq(docId), any())).thenAnswer(invocation -> {
            DocumentContentCallback<String> callback = invocation.getArgument(1);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            return callback.doWithContent(stream, mimeType, filename, bytes.length);
        });
    }

    @Test
    @DisplayName("PROC-04: Should extract text directly for text/plain documents streamed from Knowledge Service")
    void testExtractPlainTextDirectly() {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("test.txt")
                .originalFilename("test.txt")
                .mimeType("text/plain")
                .fileExtension(".txt")
                .build();

        stubStreamContent(docId, "Streamed plain text from Knowledge Service", "text/plain", "test.txt");

        String extracted = client.extractText(doc);
        assertNotNull(extracted);
        assertEquals("Streamed plain text from Knowledge Service", extracted);
        verify(knowledgeServiceClient, times(1)).streamDocumentContent(eq(docId), any());
    }

    @Test
    @DisplayName("PROC-04: Should extract text from PDF via Multimodal Service after streaming from Knowledge Service")
    void testExtractPdfViaService() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("test.pdf")
                .originalFilename("test.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        stubStreamContent(docId, "Mock PDF Content", "application/pdf", "test.pdf");

        PdfResponseDto pdfResponse = PdfResponseDto.builder()
                .text("Extracted PDF content")
                .pages(1)
                .build();

        mockServer.expect(requestTo("http://localhost:8003/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Secret", "test-internal-secret"))
                .andExpect(header("X-Internal-Secret", "test-internal-secret"))
                .andExpect(header("X-Internal-Caller-Service", "processing-service"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(pdfResponse), MediaType.APPLICATION_JSON));

        String extracted = client.extractText(doc);
        assertEquals("Extracted PDF content", extracted);
        mockServer.verify();
        verify(knowledgeServiceClient, times(1)).streamDocumentContent(eq(docId), any());
    }

    @Test
    @DisplayName("PROC-04: Should extract text from Image OCR via Multimodal Service")
    void testExtractImageOcrViaService() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("receipt.png")
                .originalFilename("receipt.png")
                .mimeType("image/png")
                .fileExtension(".png")
                .build();

        stubStreamContent(docId, "Mock PNG Image Bytes", "image/png", "receipt.png");

        OcrResponseDto ocrResponse = OcrResponseDto.builder()
                .text("Extracted OCR text from receipt")
                .build();

        mockServer.expect(requestTo("http://localhost:8003/api/v1/multimodal/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Secret", "test-internal-secret"))
                .andExpect(header("X-Internal-Secret", "test-internal-secret"))
                .andExpect(header("X-Internal-Caller-Service", "processing-service"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(ocrResponse), MediaType.APPLICATION_JSON));

        String extracted = client.extractText(doc);
        assertEquals("Extracted OCR text from receipt", extracted);
        mockServer.verify();
        verify(knowledgeServiceClient, times(1)).streamDocumentContent(eq(docId), any());
    }

    @Test
    @DisplayName("PROC-04: Should extract text from Audio via Multimodal Speech transcribe")
    void testExtractAudioSpeechViaService() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("recording.wav")
                .originalFilename("recording.wav")
                .mimeType("audio/wav")
                .fileExtension(".wav")
                .build();

        stubStreamContent(docId, "Mock Audio Bytes", "audio/wav", "recording.wav");

        SpeechResponseDto speechResponse = SpeechResponseDto.builder()
                .text("Transcribed audio content")
                .language("en")
                .build();

        mockServer.expect(requestTo("http://localhost:8003/api/v1/multimodal/speech/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Secret", "test-internal-secret"))
                .andExpect(header("X-Internal-Secret", "test-internal-secret"))
                .andExpect(header("X-Internal-Caller-Service", "processing-service"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(speechResponse), MediaType.APPLICATION_JSON));

        String extracted = client.extractText(doc);
        assertEquals("Transcribed audio content", extracted);
        mockServer.verify();
        verify(knowledgeServiceClient, times(1)).streamDocumentContent(eq(docId), any());
    }

    @Test
    @DisplayName("PROC-04: CRITICAL - No filename-to-byte fallback when Knowledge Service fails")
    void testNoFilenameByteFallbackWhenKnowledgeServiceFails() {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("Important Confidential Document Content")
                .originalFilename("confidential.txt")
                .mimeType("text/plain")
                .fileExtension(".txt")
                .build();

        when(knowledgeServiceClient.streamDocumentContent(eq(docId), any()))
                .thenThrow(new DocumentNotFoundException("Document content missing in Knowledge Service: " + docId));

        assertThrows(DocumentNotFoundException.class, () -> client.extractText(doc));
    }

    @Test
    @DisplayName("PROC-04: CRITICAL - No local-container storage-path dependency; streams through Knowledge Service API")
    void testNoLocalContainerStoragePathDependency() throws Exception {
        Path tempFile = Files.createTempFile("omragul_test_local_", ".txt");
        Files.writeString(tempFile, "Local Disk Content That Should NOT Be Read Directly");

        try {
            UUID docId = UUID.randomUUID();
            DocumentDto doc = DocumentDto.builder()
                    .id(docId)
                    .name("remote.txt")
                    .originalFilename("remote.txt")
                    .storagePath(tempFile.toString()) // Storage path exists locally
                    .mimeType("text/plain")
                    .fileExtension(".txt")
                    .build();

            // Stub Knowledge Service to return stream content
            stubStreamContent(docId, "Real Remote Content Streamed From Knowledge Service", "text/plain", "remote.txt");

            String extracted = client.extractText(doc);

            // Verified: It received content streamed from Knowledge Service, NOT from local disk!
            assertEquals("Real Remote Content Streamed From Knowledge Service", extracted);
            verify(knowledgeServiceClient, times(1)).streamDocumentContent(eq(docId), any());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("PROC-06: Should chunk text via remote Multimodal Service using JSON request body (no local bypass)")
    void testCreateChunksViaJsonBodySuccess() throws Exception {
        String longText = "Sentence one. ".repeat(150); // ~2100 characters

        ChunkResponseDto mockResponse = ChunkResponseDto.builder()
                .chunks(List.of("Sentence one. Chunk 1", "Sentence one. Chunk 2"))
                .totalChunks(2)
                .build();

        mockServer.expect(requestTo("http://localhost:8003/api/v1/multimodal/chunk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Secret", "test-internal-secret"))
                .andExpect(header("X-Internal-Secret", "test-internal-secret"))
                .andExpect(header("X-Internal-Caller-Service", "processing-service"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value(longText))
                .andExpect(jsonPath("$.chunk_size").value(1000))
                .andExpect(jsonPath("$.chunk_overlap").value(200))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mockResponse), MediaType.APPLICATION_JSON));

        List<String> chunks = client.createChunks(longText);
        assertNotNull(chunks);
        assertEquals(2, chunks.size());
        assertEquals("Sentence one. Chunk 1", chunks.get(0));
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-06: Should throw DownstreamServiceException on 5xx error from Multimodal chunk service")
    void testCreateChunksServerError5xx() {
        mockServer.expect(requestTo("http://localhost:8003/api/v1/multimodal/chunk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"detail\":\"Chunking failure\"}"));

        assertThrows(DownstreamServiceException.class, () -> client.createChunks("Valid text to chunk"));
        mockServer.verify();
    }

    @Test
    @DisplayName("PROC-06: Should throw DownstreamServiceException on 4xx client error from Multimodal chunk service")
    void testCreateChunksClientError4xx() {
        mockServer.expect(requestTo("http://localhost:8003/api/v1/multimodal/chunk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"detail\":\"Invalid parameters\"}"));

        assertThrows(DownstreamServiceException.class, () -> client.createChunks("Valid text"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw DownstreamServiceException on 5xx error from Multimodal Service")
    void testExtractPdfServerError5xx() {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("error.pdf")
                .originalFilename("error.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        stubStreamContent(docId, "Mock PDF Content", "application/pdf", "error.pdf");

        mockServer.expect(requestTo("http://localhost:8003/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(DownstreamServiceException.class, () -> client.extractText(doc));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw DownstreamServiceException on network/connection failure to Multimodal Service")
    void testExtractPdfNetworkTimeout() {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("timeout.pdf")
                .originalFilename("timeout.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        stubStreamContent(docId, "Mock PDF Content", "application/pdf", "timeout.pdf");

        mockServer.expect(requestTo("http://localhost:8003/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));

        assertThrows(DownstreamServiceException.class, () -> client.extractText(doc));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return empty list for empty text chunks")
    void testEmptyChunks() {
        List<String> chunks = client.createChunks("");
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("FINDING-3.4-01: Interrupted or oversized stream halts extraction immediately without sending partial data to extraction service")
    void testOversizedOrInterruptedStreamNeverReachesExtraction() {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("oversized.pdf")
                .originalFilename("oversized.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        when(knowledgeServiceClient.streamDocumentContent(eq(docId), any())).thenThrow(
                new com.omragul.processing.exception.PermanentProcessingException("FILE_TOO_LARGE", "File limit exceeded")
        );

        assertThrows(com.omragul.processing.exception.PermanentProcessingException.class, () ->
                client.extractText(doc));

        // Extraction service endpoint must never be reached
        mockServer.verify();
    }

    @Test
    @DisplayName("SEC-3.7-B2B: createChunks fails closed before HTTP request when secret is blank")
    void testCreateChunksThrowsIllegalStateExceptionWhenSecretIsBlank() {
        MultimodalServiceClientImpl unauthedClient = new MultimodalServiceClientImpl(
                knowledgeServiceClient, "http://localhost:8003", 2000, 5000, ""
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                unauthedClient.createChunks("Some valid text to chunk"));
        assertTrue(ex.getMessage().contains("INTERNAL_SERVICE_SECRET is not configured"));
    }

    @Test
    @DisplayName("SEC-3.7-B2B: extractPdfText fails closed before HTTP request when secret is blank")
    void testExtractPdfThrowsIllegalStateExceptionWhenSecretIsBlank() {
        MultimodalServiceClientImpl unauthedClient = new MultimodalServiceClientImpl(
                knowledgeServiceClient, "http://localhost:8003", 2000, 5000, ""
        );
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("test.pdf")
                .originalFilename("test.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();
        stubStreamContent(docId, "Mock PDF Content", "application/pdf", "test.pdf");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                unauthedClient.extractText(doc));
        assertTrue(ex.getMessage().contains("INTERNAL_SERVICE_SECRET is not configured"));
    }

    @Test
    @DisplayName("SEC-3.7-B2B: extractImageOcrText fails closed before HTTP request when secret is blank")
    void testExtractImageOcrThrowsIllegalStateExceptionWhenSecretIsBlank() {
        MultimodalServiceClientImpl unauthedClient = new MultimodalServiceClientImpl(
                knowledgeServiceClient, "http://localhost:8003", 2000, 5000, "   "
        );
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("test.png")
                .originalFilename("test.png")
                .mimeType("image/png")
                .fileExtension(".png")
                .build();
        stubStreamContent(docId, "Mock Image Bytes", "image/png", "test.png");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                unauthedClient.extractText(doc));
        assertTrue(ex.getMessage().contains("INTERNAL_SERVICE_SECRET is not configured"));
    }

    @Test
    @DisplayName("SEC-3.7-B2B: extractAudioSpeechText fails closed before HTTP request when secret is blank")
    void testExtractAudioSpeechThrowsIllegalStateExceptionWhenSecretIsBlank() {
        MultimodalServiceClientImpl unauthedClient = new MultimodalServiceClientImpl(
                knowledgeServiceClient, "http://localhost:8003", 2000, 5000, null
        );
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("test.wav")
                .originalFilename("test.wav")
                .mimeType("audio/wav")
                .fileExtension(".wav")
                .build();
        stubStreamContent(docId, "Mock Audio Bytes", "audio/wav", "test.wav");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                unauthedClient.extractText(doc));
        assertTrue(ex.getMessage().contains("INTERNAL_SERVICE_SECRET is not configured"));
    }
}
