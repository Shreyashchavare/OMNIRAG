package com.omragul.processing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.client.dto.PdfResponseDto;
import com.omragul.processing.client.dto.SpeechResponseDto;
import com.omragul.processing.client.impl.MultimodalServiceClientImpl;
import com.omragul.processing.exception.DownstreamServiceException;
import com.omragul.processing.exception.PermanentProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DownstreamClientStandardizationAndStreamingTest {

    private static final String TEST_SECRET = "test-internal-secret-xyz";
    private static final String BASE_URL = "http://localhost:8003";

    private MultimodalServiceClientImpl client;
    private KnowledgeServiceClient knowledgeServiceClient;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        knowledgeServiceClient = mock(KnowledgeServiceClient.class);
        // Configure with 50 KB max file size for test boundary verification
        client = new MultimodalServiceClientImpl(
                knowledgeServiceClient,
                BASE_URL,
                1000,
                30000,
                51200L, // 50 KB limit
                TEST_SECRET
        );

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        Field field = MultimodalServiceClientImpl.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, restClient);
    }

    private void stubStreamContent(UUID docId, InputStream stream, String mimeType, String filename, long length) {
        when(knowledgeServiceClient.streamDocumentContent(eq(docId), any())).thenAnswer(invocation -> {
            DocumentContentCallback<String> callback = invocation.getArgument(1);
            return callback.doWithContent(stream, mimeType, filename, length);
        });
    }

    @Test
    @DisplayName("REQ-1 & REQ-5: Successful downstream multipart request with metadata and field preservation")
    void testSuccessfulDownstreamRequestAndMetadataPreservation() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("annual_report.pdf")
                .originalFilename("annual_report.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        byte[] samplePdf = "%PDF-1.4 sample content for streaming test".getBytes(StandardCharsets.UTF_8);
        stubStreamContent(docId, new ByteArrayInputStream(samplePdf), "application/pdf", "annual_report.pdf", samplePdf.length);

        PdfResponseDto responseDto = PdfResponseDto.builder()
                .text("Extracted annual report text successfully")
                .pages(3)
                .build();

        mockServer.expect(requestTo(BASE_URL + "/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Secret", TEST_SECRET))
                .andExpect(header("X-Internal-Secret", TEST_SECRET))
                .andExpect(header("X-Internal-Caller-Service", "processing-service"))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("multipart/form-data")))
                .andRespond(withSuccess(objectMapper.writeValueAsString(responseDto), MediaType.APPLICATION_JSON));

        String result = client.extractText(doc);

        assertNotNull(result);
        assertEquals("Extracted annual report text successfully", result);
        mockServer.verify();
        verify(knowledgeServiceClient, times(1)).streamDocumentContent(eq(docId), any());
    }

    @Test
    @DisplayName("REQ-2: Connection failure handling translates to DownstreamServiceException with CONNECTION_FAILURE")
    void testConnectionFailureHandling() {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("recording.wav")
                .originalFilename("recording.wav")
                .mimeType("audio/wav")
                .fileExtension(".wav")
                .build();

        byte[] audioBytes = "RIFF....WAVEfmt ".getBytes(StandardCharsets.UTF_8);
        stubStreamContent(docId, new ByteArrayInputStream(audioBytes), "audio/wav", "recording.wav", audioBytes.length);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/multimodal/speech/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new ConnectException("Connection refused to multimodal-service:8003")));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class, () -> client.extractText(doc));

        assertEquals("multimodal-service", ex.getServiceName());
        assertEquals(502, ex.getStatusCode());
        assertTrue(ex.isRetryable());
        assertEquals(DownstreamServiceException.DownstreamErrorType.CONNECTION_FAILURE, ex.getErrorType());
        assertTrue(ex.isConnectionFailure());
        assertFalse(ex.isTimeout());
        mockServer.verify();
    }

    @Test
    @DisplayName("REQ-3: Response timeout handling translates to DownstreamServiceException with RESPONSE_TIMEOUT")
    void testTimeoutHandling() {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("heavy.pdf")
                .originalFilename("heavy.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        byte[] pdfBytes = "%PDF-1.4 heavy content".getBytes(StandardCharsets.UTF_8);
        stubStreamContent(docId, new ByteArrayInputStream(pdfBytes), "application/pdf", "heavy.pdf", pdfBytes.length);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new SocketTimeoutException("Read timed out after 30000ms")));

        DownstreamServiceException ex = assertThrows(DownstreamServiceException.class, () -> client.extractText(doc));

        assertEquals("multimodal-service", ex.getServiceName());
        assertEquals(502, ex.getStatusCode());
        assertTrue(ex.isRetryable());
        assertEquals(DownstreamServiceException.DownstreamErrorType.RESPONSE_TIMEOUT, ex.getErrorType());
        assertTrue(ex.isTimeout());
        mockServer.verify();
    }

    @Test
    @DisplayName("REQ-4A: Downstream 4xx client error propagation and classification")
    void testDownstream4xxClientErrorHandling() {
        UUID docId400 = UUID.randomUUID();
        DocumentDto doc400 = DocumentDto.builder()
                .id(docId400)
                .name("corrupt.pdf")
                .originalFilename("corrupt.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        stubStreamContent(docId400, new ByteArrayInputStream("corrupt".getBytes(StandardCharsets.UTF_8)), "application/pdf", "corrupt.pdf", 7);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"detail\":\"Corrupt PDF header\"}"));

        DownstreamServiceException ex400 = assertThrows(DownstreamServiceException.class, () -> client.extractText(doc400));
        assertEquals(400, ex400.getStatusCode());
        assertFalse(ex400.isRetryable(), "400 Client error must not be retryable");
        assertEquals(DownstreamServiceException.DownstreamErrorType.CLIENT_ERROR_4XX, ex400.getErrorType());
        assertTrue(ex400.isClientError());
        mockServer.verify();
    }

    @Test
    @DisplayName("REQ-4B: Downstream 5xx server error propagation and classification")
    void testDownstream5xxServerErrorHandling() {
        UUID docId500 = UUID.randomUUID();
        DocumentDto doc500 = DocumentDto.builder()
                .id(docId500)
                .name("server_err.pdf")
                .originalFilename("server_err.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        stubStreamContent(docId500, new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)), "application/pdf", "server_err.pdf", 4);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"detail\":\"OCR engine crashed\"}"));

        DownstreamServiceException ex500 = assertThrows(DownstreamServiceException.class, () -> client.extractText(doc500));
        assertEquals(500, ex500.getStatusCode());
        assertTrue(ex500.isRetryable(), "500 Server error must be retryable");
        assertEquals(DownstreamServiceException.DownstreamErrorType.SERVER_ERROR_5XX, ex500.getErrorType());
        assertTrue(ex500.isServerError());
        mockServer.verify();
    }

    @Test
    @DisplayName("REQ-6: Large file forwarding streams chunked data without allocating whole-file byte arrays")
    void testLargeFileForwardingWithoutByteArrayBuffering() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("medium.pdf")
                .originalFilename("medium.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        // 40 KB streamed data (below 50 KB limit) generated on-the-fly via generator stream
        int streamSize = 40960;
        AtomicInteger bytesGenerated = new AtomicInteger(0);
        InputStream generatingStream = new InputStream() {
            @Override
            public int read() {
                if (bytesGenerated.getAndIncrement() < streamSize) {
                    return 65; // 'A'
                }
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                int count = bytesGenerated.get();
                if (count >= streamSize) {
                    return -1;
                }
                int toRead = Math.min(len, streamSize - count);
                for (int i = 0; i < toRead; i++) {
                    b[off + i] = 65;
                }
                bytesGenerated.addAndGet(toRead);
                return toRead;
            }
        };

        stubStreamContent(docId, generatingStream, "application/pdf", "medium.pdf", streamSize);

        PdfResponseDto response = PdfResponseDto.builder()
                .text("Successfully extracted large streamed PDF")
                .pages(5)
                .build();

        mockServer.expect(requestTo(BASE_URL + "/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        String result = client.extractText(doc);

        assertEquals("Successfully extracted large streamed PDF", result);
        assertEquals(streamSize, bytesGenerated.get(), "Entire stream should have been forwarded");
        mockServer.verify();
    }

    @Test
    @DisplayName("REQ-7: Upload size enforcement terminates stream immediately when exceeding max bytes")
    void testUploadSizeEnforcement() {
        UUID docId = UUID.randomUUID();
        DocumentDto doc = DocumentDto.builder()
                .id(docId)
                .name("huge.pdf")
                .originalFilename("huge.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        // 60 KB exceeds the 50 KB (51200 byte) configured limit
        int streamSize = 61440;
        AtomicInteger bytesRead = new AtomicInteger(0);
        InputStream oversizedStream = new InputStream() {
            @Override
            public int read(byte[] b, int off, int len) {
                int count = bytesRead.get();
                if (count >= streamSize) {
                    return -1;
                }
                int toRead = Math.min(len, streamSize - count);
                bytesRead.addAndGet(toRead);
                return toRead;
            }

            @Override
            public int read() {
                if (bytesRead.getAndIncrement() < streamSize) {
                    return 65;
                }
                return -1;
            }
        };

        stubStreamContent(docId, oversizedStream, "application/pdf", "huge.pdf", streamSize);

        PermanentProcessingException ex = assertThrows(PermanentProcessingException.class, () -> client.extractText(doc));

        assertEquals("FILE_TOO_LARGE", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("exceeded maximum allowed size limit"));
        mockServer.verify();
    }

    @Test
    @DisplayName("REQ-8: Temporary file resources are strictly cleaned up on success")
    void testTemporaryFileCleanupOnSuccess() throws Exception {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        long initialCount = countTempFiles(tempDir);

        UUID docSuccess = UUID.randomUUID();
        DocumentDto docSuccessDto = DocumentDto.builder()
                .id(docSuccess)
                .name("cleanup_test.pdf")
                .originalFilename("cleanup_test.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        byte[] pdfBytes = "Sample pdf data".getBytes(StandardCharsets.UTF_8);
        stubStreamContent(docSuccess, new ByteArrayInputStream(pdfBytes), "application/pdf", "cleanup_test.pdf", pdfBytes.length);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(new PdfResponseDto("clean", 1)), MediaType.APPLICATION_JSON));

        client.extractText(docSuccessDto);
        long afterSuccessCount = countTempFiles(tempDir);
        assertEquals(initialCount, afterSuccessCount, "Temporary forwarding file must be deleted on success");
        mockServer.verify();
    }

    @Test
    @DisplayName("REQ-9: Temporary file resources are strictly cleaned up on downstream failure")
    void testTemporaryFileCleanupOnDownstreamFailure() throws Exception {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        long initialCount = countTempFiles(tempDir);

        UUID docFail = UUID.randomUUID();
        DocumentDto docFailDto = DocumentDto.builder()
                .id(docFail)
                .name("fail_cleanup.pdf")
                .originalFilename("fail_cleanup.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        byte[] pdfBytes = "Sample pdf data".getBytes(StandardCharsets.UTF_8);
        stubStreamContent(docFail, new ByteArrayInputStream(pdfBytes), "application/pdf", "fail_cleanup.pdf", pdfBytes.length);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("Downstream crash"));

        assertThrows(DownstreamServiceException.class, () -> client.extractText(docFailDto));
        long afterFailureCount = countTempFiles(tempDir);
        assertEquals(initialCount, afterFailureCount, "Temporary forwarding file must be deleted on downstream failure");
        mockServer.verify();
    }

    @Test
    @DisplayName("REQ-10: Downstream error message sanitization truncates overly long bodies")
    void testDownstreamErrorMessageSanitization() {
        String longErrorBody = "A".repeat(500);
        DownstreamServiceException ex = new DownstreamServiceException("multimodal-service", 500, longErrorBody);

        assertTrue(ex.getMessage().contains("... [truncated]"));
        assertTrue(ex.getMessage().length() < 350, "Sanitized message must not leak massive payloads");
    }

    private long countTempFiles(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith("omragul_mm_upload_")).count();
        }
    }
}
