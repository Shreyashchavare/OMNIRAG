package com.omragul.processing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.client.dto.ChunkResponseDto;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.client.dto.PdfResponseDto;
import com.omragul.processing.client.impl.MultimodalServiceClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MultimodalServiceClientTest {

    private MultimodalServiceClientImpl client;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = new MultimodalServiceClientImpl("http://localhost:8004", 2000, 5000);

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8004");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        Field field = MultimodalServiceClientImpl.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, restClient);
    }

    @Test
    @DisplayName("Should extract text directly for text/plain documents")
    void testExtractPlainTextDirectly() {
        DocumentDto doc = DocumentDto.builder()
                .id(UUID.randomUUID())
                .name("This is inline plain text content for testing")
                .mimeType("text/plain")
                .fileExtension(".txt")
                .build();

        String extracted = client.extractText(doc);
        assertNotNull(extracted);
        assertEquals("This is inline plain text content for testing", extracted);
    }

    @Test
    @DisplayName("Should extract text from PDF via Multimodal Service")
    void testExtractPdfViaService() throws Exception {
        DocumentDto doc = DocumentDto.builder()
                .id(UUID.randomUUID())
                .name("test.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        PdfResponseDto pdfResponse = PdfResponseDto.builder()
                .text("Extracted PDF content")
                .pages(1)
                .build();

        mockServer.expect(requestTo("http://localhost:8004/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(pdfResponse), MediaType.APPLICATION_JSON));

        String extracted = client.extractText(doc);
        assertEquals("Extracted PDF content", extracted);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should chunk text locally when long or when service call fails")
    void testChunkLocally() {
        String longText = "Sentence one. ".repeat(150); // ~2100 characters

        List<String> chunks = client.createChunks(longText);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 2);
    }

    @Test
    @DisplayName("Should extract text from Image OCR via Multimodal Service")
    void testExtractImageOcrViaService() throws Exception {
        DocumentDto doc = DocumentDto.builder()
                .id(UUID.randomUUID())
                .name("receipt.png")
                .mimeType("image/png")
                .fileExtension(".png")
                .build();

        com.omragul.processing.client.dto.OcrResponseDto ocrResponse = com.omragul.processing.client.dto.OcrResponseDto.builder()
                .text("Extracted OCR text from receipt")
                .build();

        mockServer.expect(requestTo("http://localhost:8004/api/v1/multimodal/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(ocrResponse), MediaType.APPLICATION_JSON));

        String extracted = client.extractText(doc);
        assertEquals("Extracted OCR text from receipt", extracted);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should extract text from Audio via Multimodal Speech transcribe")
    void testExtractAudioSpeechViaService() throws Exception {
        DocumentDto doc = DocumentDto.builder()
                .id(UUID.randomUUID())
                .name("recording.wav")
                .mimeType("audio/wav")
                .fileExtension(".wav")
                .build();

        com.omragul.processing.client.dto.SpeechResponseDto speechResponse = com.omragul.processing.client.dto.SpeechResponseDto.builder()
                .text("Transcribed audio content")
                .language("en")
                .build();

        mockServer.expect(requestTo("http://localhost:8004/api/v1/multimodal/speech/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(speechResponse), MediaType.APPLICATION_JSON));

        String extracted = client.extractText(doc);
        assertEquals("Transcribed audio content", extracted);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw DownstreamServiceException on 5xx error from Multimodal Service")
    void testExtractPdfServerError5xx() {
        DocumentDto doc = DocumentDto.builder()
                .id(UUID.randomUUID())
                .name("error.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        mockServer.expect(requestTo("http://localhost:8004/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.extractText(doc));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw DownstreamServiceException on network/connection failure to Multimodal Service")
    void testExtractPdfNetworkTimeout() {
        DocumentDto doc = DocumentDto.builder()
                .id(UUID.randomUUID())
                .name("timeout.pdf")
                .mimeType("application/pdf")
                .fileExtension(".pdf")
                .build();

        mockServer.expect(requestTo("http://localhost:8004/api/v1/multimodal/pdf/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));

        assertThrows(com.omragul.processing.exception.DownstreamServiceException.class,
                () -> client.extractText(doc));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return empty list for empty text chunks")
    void testEmptyChunks() {
        List<String> chunks = client.createChunks("");
        assertTrue(chunks.isEmpty());
    }
}
