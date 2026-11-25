package com.omragul.processing.client.impl;

import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.MultimodalServiceClient;
import com.omragul.processing.client.dto.ChunkRequestDto;
import com.omragul.processing.client.dto.ChunkResponseDto;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.client.dto.OcrResponseDto;
import com.omragul.processing.client.dto.PdfResponseDto;
import com.omragul.processing.client.dto.SpeechResponseDto;
import com.omragul.processing.exception.DownstreamServiceException;
import com.omragul.processing.exception.PermanentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class MultimodalServiceClientImpl implements MultimodalServiceClient {

    private final RestClient restClient;
    private final KnowledgeServiceClient knowledgeServiceClient;
    private final String internalSecret;
    private final long maxFileSizeBytes;

    @Autowired
    public MultimodalServiceClientImpl(
            KnowledgeServiceClient knowledgeServiceClient,
            @Value("${processing.multimodal-service.url:http://localhost:8003}") String baseUrl,
            @Value("${processing.orchestration.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${processing.multimodal-service.read-timeout-ms:${processing.orchestration.ai-read-timeout-ms:30000}}") int readTimeoutMs,
            @Value("${processing.orchestration.max-file-size-bytes:104857600}") long maxFileSizeBytes,
            @Value("${internal.service.secret:}") String internalSecret
    ) {
        this.knowledgeServiceClient = knowledgeServiceClient;
        this.internalSecret = internalSecret;
        this.maxFileSizeBytes = maxFileSizeBytes;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public MultimodalServiceClientImpl(
            KnowledgeServiceClient knowledgeServiceClient,
            String baseUrl,
            int connectTimeoutMs,
            int readTimeoutMs,
            String internalSecret
    ) {
        this(knowledgeServiceClient, baseUrl, connectTimeoutMs, readTimeoutMs, 104857600L, internalSecret);
    }

    public MultimodalServiceClientImpl(
            KnowledgeServiceClient knowledgeServiceClient,
            String baseUrl,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
        this(knowledgeServiceClient, baseUrl, connectTimeoutMs, readTimeoutMs, 104857600L, "");
    }

    public MultimodalServiceClientImpl(
            @Value("${processing.multimodal-service.url:http://localhost:8003}") String baseUrl,
            @Value("${processing.orchestration.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${processing.multimodal-service.read-timeout-ms:${processing.orchestration.ai-read-timeout-ms:30000}}") int readTimeoutMs
    ) {
        this(null, baseUrl, connectTimeoutMs, readTimeoutMs, 104857600L, "");
    }

    @Override
    public String extractText(DocumentDto document) {
        if (document == null || document.getId() == null) {
            throw new IllegalArgumentException("Document and document ID are required for extraction");
        }
        log.info("Extracting text for document: {} (name: {}, mime: {})",
                document.getId(), document.getName(), document.getMimeType());

        if (knowledgeServiceClient == null) {
            throw new IllegalStateException("KnowledgeServiceClient is not configured for document content retrieval");
        }

        String mimeType = document.getMimeType() != null ? document.getMimeType().toLowerCase() : "";
        String extension = document.getFileExtension() != null ? document.getFileExtension().toLowerCase() : "";

        return knowledgeServiceClient.streamDocumentContent(document.getId(), (inputStream, contentType, filename, contentLength) -> {
            String effectiveFilename = (filename != null && !filename.isBlank()) ? filename :
                    (document.getOriginalFilename() != null ? document.getOriginalFilename() : "document");

            try {
                if (isTextFormat(mimeType, extension)) {
                    log.info("Directly reading text content for document: {}", document.getId());
                    byte[] bytes = inputStream.readAllBytes();
                    if (bytes.length == 0) {
                        throw new PermanentProcessingException("EMPTY_TEXT", "Document content is empty for document: " + document.getId());
                    }
                    return new String(bytes, StandardCharsets.UTF_8);
                } else if (isPdfFormat(mimeType, extension)) {
                    return extractPdfText(inputStream, effectiveFilename);
                } else if (isImageFormat(mimeType, extension)) {
                    String effectiveContentType = (contentType != null && !contentType.isBlank()) ? contentType : "image/jpeg";
                    return extractImageOcrText(inputStream, effectiveFilename, effectiveContentType);
                } else if (isAudioFormat(mimeType, extension)) {
                    String effectiveContentType = (contentType != null && !contentType.isBlank()) ? contentType : "audio/wav";
                    return extractAudioSpeechText(inputStream, effectiveFilename, effectiveContentType);
                } else {
                    log.warn("Unrecognized MIME type {} or extension {}, attempting UTF-8 decode", mimeType, extension);
                    byte[] fileBytes = inputStream.readAllBytes();
                    if (fileBytes.length == 0) {
                        throw new PermanentProcessingException("EMPTY_CONTENT", "Document content is empty for document: " + document.getId());
                    }
                    return new String(fileBytes, StandardCharsets.UTF_8);
                }
            } catch (DownstreamServiceException | PermanentProcessingException | IllegalStateException e) {
                throw e;
            } catch (ResourceAccessException e) {
                log.error("Network/timeout error connecting to Multimodal Service: {}", e.getMessage());
                throw DownstreamServiceException.fromResourceAccessException("multimodal-service", e);
            } catch (Exception e) {
                log.error("Error during extraction for document {}: {}", document.getId(), e.getMessage());
                throw new DownstreamServiceException("multimodal-service", "Failed to extract text from document", e);
            }
        });
    }

    private void validateInternalSecret() {
        if (internalSecret == null || internalSecret.isBlank()) {
            log.error("INTERNAL_SERVICE_SECRET configuration is missing for MultimodalServiceClient");
            throw new IllegalStateException("INTERNAL_SERVICE_SECRET is not configured for MultimodalServiceClient");
        }
    }

    @Override
    public List<String> createChunks(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        validateInternalSecret();

        log.info("Calling Multimodal Service POST /api/v1/multimodal/chunk with JSON body for text of length {}", text.length());
        try {
            ChunkResponseDto response = restClient.post()
                    .uri("/api/v1/multimodal/chunk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Service-Secret", internalSecret)
                    .header("X-Internal-Secret", internalSecret)
                    .header("X-Internal-Caller-Service", "processing-service")
                    .body(new ChunkRequestDto(text))
                    .retrieve()
                    .body(ChunkResponseDto.class);

            if (response == null || response.getChunks() == null || response.getChunks().isEmpty()) {
                throw new PermanentProcessingException("EMPTY_CHUNKS", "Multimodal chunk service returned 0 chunks");
            }
            return response.getChunks();
        } catch (DownstreamServiceException | PermanentProcessingException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Client error from Multimodal chunk service: {} - {}", e.getStatusCode(), e.getMessage());
            throw DownstreamServiceException.fromHttpClientErrorException("multimodal-service", e);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("Server error from Multimodal chunk service: {} - {}", e.getStatusCode(), e.getMessage());
            throw DownstreamServiceException.fromHttpServerErrorException("multimodal-service", e);
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error connecting to Multimodal chunk service: {}", e.getMessage());
            throw DownstreamServiceException.fromResourceAccessException("multimodal-service", e);
        } catch (Exception e) {
            log.error("Error creating chunks via Multimodal Service: {}", e.getMessage());
            throw new DownstreamServiceException("multimodal-service", "Failed to create chunks", e);
        }
    }

    private <T> T forwardMultipartFile(
            InputStream inputStream,
            String filename,
            String contentType,
            String uri,
            Class<T> responseType
    ) {
        validateInternalSecret();
        log.info("Forwarding multipart file {} ({}) to Multimodal Service POST {}", filename, contentType, uri);

        Path tempFile = null;
        try {
            tempFile = spoolToTempFile(inputStream, filename);

            Resource resource = new NamedFileSystemResource(tempFile.toFile(), filename, contentType);

            HttpHeaders partHeaders = new HttpHeaders();
            if (contentType != null && !contentType.isBlank()) {
                partHeaders.setContentType(MediaType.parseMediaType(contentType));
            }
            HttpEntity<Resource> filePart = new HttpEntity<>(resource, partHeaders);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", filePart);

            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("X-Internal-Service-Secret", internalSecret)
                    .header("X-Internal-Secret", internalSecret)
                    .header("X-Internal-Caller-Service", "processing-service")
                    .body(body)
                    .retrieve()
                    .body(responseType);

        } catch (DownstreamServiceException | PermanentProcessingException | IllegalStateException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Client error from Multimodal Service on {}: {} - {}", uri, e.getStatusCode(), e.getMessage());
            throw DownstreamServiceException.fromHttpClientErrorException("multimodal-service", e);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("Server error from Multimodal Service on {}: {} - {}", uri, e.getStatusCode(), e.getMessage());
            throw DownstreamServiceException.fromHttpServerErrorException("multimodal-service", e);
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error connecting to Multimodal Service on {}: {}", uri, e.getMessage());
            throw DownstreamServiceException.fromResourceAccessException("multimodal-service", e);
        } catch (Exception e) {
            log.error("Error forwarding multipart file to Multimodal Service on {}: {}", uri, e.getMessage());
            throw new DownstreamServiceException("multimodal-service", "Failed to forward file to Multimodal Service", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("Cleaned up temporary multipart forwarding file: {}", tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary multipart file {}: {}", tempFile, e.getMessage());
                }
            }
        }
    }

    private Path spoolToTempFile(InputStream in, String filename) {
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("omragul_mm_upload_", ".tmp");
            long totalBytes = 0;
            byte[] buffer = new byte[65536]; // 64 KB streaming buffer
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempPath), 65536)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    totalBytes += read;
                    if (maxFileSizeBytes > 0 && totalBytes > maxFileSizeBytes) {
                        log.error("File {} exceeded maximum upload limit of {} bytes (read {} bytes)",
                                filename, maxFileSizeBytes, totalBytes);
                        throw new PermanentProcessingException("FILE_TOO_LARGE",
                                String.format("File %s exceeded maximum allowed size limit of %d bytes",
                                        filename, maxFileSizeBytes));
                    }
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            if (totalBytes == 0) {
                throw new PermanentProcessingException("EMPTY_CONTENT",
                        "Document content is empty for file: " + filename);
            }

            log.debug("Spooled {} bytes to temp file {} for {}", totalBytes, tempPath, filename);
            return tempPath;
        } catch (PermanentProcessingException e) {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
            throw e;
        } catch (IOException e) {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
            log.error("I/O error spooling document stream for {}: {}", filename, e.getMessage());
            throw new DownstreamServiceException("processing-service",
                    "Failed to spool document stream for forwarding", e);
        }
    }

    private String extractPdfText(InputStream inputStream, String filename) {
        PdfResponseDto response = forwardMultipartFile(
                inputStream, filename, "application/pdf",
                "/api/v1/multimodal/pdf/extract", PdfResponseDto.class
        );
        if (response == null || response.getText() == null || response.getText().isBlank()) {
            throw new PermanentProcessingException("EMPTY_EXTRACTION", "No text could be extracted from PDF: " + filename);
        }
        return response.getText();
    }

    public String extractPdfText(byte[] fileBytes, String filename) {
        return extractPdfText(new ByteArrayInputStream(fileBytes), filename);
    }

    private String extractImageOcrText(InputStream inputStream, String filename, String contentType) {
        String effectiveContentType = (contentType != null && !contentType.isBlank()) ? contentType : "image/jpeg";
        OcrResponseDto response = forwardMultipartFile(
                inputStream, filename, effectiveContentType,
                "/api/v1/multimodal/ocr", OcrResponseDto.class
        );
        if (response == null || response.getText() == null || response.getText().isBlank()) {
            throw new PermanentProcessingException("EMPTY_EXTRACTION", "No text could be extracted from image OCR: " + filename);
        }
        return response.getText();
    }

    public String extractImageOcrText(byte[] fileBytes, String filename) {
        return extractImageOcrText(new ByteArrayInputStream(fileBytes), filename, "image/jpeg");
    }

    private String extractAudioSpeechText(InputStream inputStream, String filename, String contentType) {
        String effectiveContentType = (contentType != null && !contentType.isBlank()) ? contentType : "audio/wav";
        SpeechResponseDto response = forwardMultipartFile(
                inputStream, filename, effectiveContentType,
                "/api/v1/multimodal/speech/transcribe", SpeechResponseDto.class
        );
        if (response == null || response.getText() == null || response.getText().isBlank()) {
            throw new PermanentProcessingException("EMPTY_EXTRACTION", "No speech transcription generated for: " + filename);
        }
        return response.getText();
    }

    public String extractAudioSpeechText(byte[] fileBytes, String filename) {
        return extractAudioSpeechText(new ByteArrayInputStream(fileBytes), filename, "audio/wav");
    }

    private boolean isTextFormat(String mime, String ext) {
        return mime.startsWith("text/") || ext.equals(".txt") || ext.equals(".md")
                || ext.equals(".markdown") || ext.equals(".json") || ext.equals(".csv");
    }

    private boolean isPdfFormat(String mime, String ext) {
        return mime.equals("application/pdf") || ext.equals(".pdf");
    }

    private boolean isImageFormat(String mime, String ext) {
        return mime.startsWith("image/") || ext.equals(".png") || ext.equals(".jpg")
                || ext.equals(".jpeg") || ext.equals(".bmp") || ext.equals(".tiff");
    }

    private boolean isAudioFormat(String mime, String ext) {
        return mime.startsWith("audio/") || ext.equals(".wav") || ext.equals(".mp3")
                || ext.equals(".m4a") || ext.equals(".ogg");
    }

    public static class NamedFileSystemResource extends FileSystemResource {
        private final String filename;
        private final String contentType;

        public NamedFileSystemResource(File file, String filename, String contentType) {
            super(file);
            this.filename = (filename != null && !filename.isBlank()) ? filename : file.getName();
            this.contentType = contentType;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }

        public String getContentType() {
            return this.contentType;
        }
    }

    public static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;
        private final String contentType;

        public NamedByteArrayResource(byte[] byteArray, String filename, String contentType) {
            super(byteArray);
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }

        public String getContentType() {
            return this.contentType;
        }
    }
}
