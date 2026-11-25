package com.omragul.processing.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.client.DocumentContentCallback;
import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.client.dto.DocumentPermissionVerificationResponse;
import com.omragul.processing.exception.DocumentNotFoundException;
import com.omragul.processing.exception.DownstreamServiceException;
import com.omragul.processing.exception.PermanentProcessingException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class KnowledgeServiceClientImpl implements KnowledgeServiceClient {

    private static final Set<String> FORBIDDEN_DEFAULTS = Set.of(
            "omragul-internal-service-secret-v2",
            "omragul-internal-dev-secret-v2",
            "changeme",
            "secret",
            "password",
            "default",
            "placeholder",
            "replace_with_a_strong_random_secret");

    private final RestClient restClient;
    private final String internalSecret;
    private final long maxFileSizeBytes;
    private final ObjectMapper objectMapper;

    @Autowired
    public KnowledgeServiceClientImpl(
            @Value("${processing.knowledge-service.url:http://localhost:8082}") String baseUrl,
            @Value("${processing.knowledge-service.secret:}") String internalSecret,
            @Value("${processing.orchestration.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${processing.orchestration.read-timeout-ms:5000}") int readTimeoutMs,
            @Value("${processing.orchestration.max-file-size-bytes:104857600}") long maxFileSizeBytes,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.internalSecret = internalSecret;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper().findAndRegisterModules();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public KnowledgeServiceClientImpl(
            @Value("${processing.knowledge-service.url:http://localhost:8082}") String baseUrl,
            @Value("${processing.knowledge-service.secret:}") String internalSecret,
            @Value("${processing.orchestration.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${processing.orchestration.read-timeout-ms:5000}") int readTimeoutMs) {
        this(baseUrl, internalSecret, connectTimeoutMs, readTimeoutMs, 104857600L,
                new ObjectMapper().findAndRegisterModules());
    }

    @PostConstruct
    public void validateConfiguration() {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException(
                    "INTERNAL_SERVICE_SECRET configuration is missing or blank for Processing Service. Application startup aborted.");
        }
        if (FORBIDDEN_DEFAULTS.contains(internalSecret.trim().toLowerCase())) {
            throw new IllegalStateException(
                    "INTERNAL_SERVICE_SECRET is set to an insecure or known development default for Processing Service. Application startup aborted.");
        }
    }

    @Override
    public Optional<DocumentDto> getDocument(UUID documentId) {
        log.debug("Calling Knowledge Service GET /api/v1/documents/{}", documentId);
        try {
            return restClient.get()
                    .uri("/api/v1/documents/{id}", documentId)
                    .header("X-Internal-Secret", internalSecret)
                    .exchange((req, res) -> {
                        int code = res.getStatusCode().value();
                        if (code == 404) {
                            log.warn("Document {} not found in Knowledge Service (HTTP 404)", documentId);
                            return Optional.empty();
                        }
                        if (res.getStatusCode().is5xxServerError()) {
                            log.error("Knowledge Service returned 5xx for document {}: HTTP {}", documentId, code);
                            throw new DownstreamServiceException("knowledge-service", code,
                                    "Knowledge Service server error: " + res.getStatusText());
                        }
                        if (res.getStatusCode().is4xxClientError()) {
                            log.warn("Knowledge Service returned 4xx for document {}: HTTP {}", documentId, code);
                            throw new DownstreamServiceException("knowledge-service", code,
                                    "Knowledge Service client error: " + res.getStatusText());
                        }
                        try {
                            byte[] body = res.getBody().readAllBytes();
                            if (body.length == 0) {
                                return Optional.empty();
                            }
                            return Optional.ofNullable(objectMapper.readValue(body, DocumentDto.class));
                        } catch (Exception e) {
                            log.error("Failed to parse DocumentDto JSON from Knowledge Service for document {}: {}",
                                    documentId, e.getMessage());
                            throw new DownstreamServiceException("knowledge-service",
                                    "Malformed response payload from Knowledge Service", e);
                        }
                    });
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpServerErrorException e) {
            log.error("Knowledge Service returned 5xx for document {}: {}", documentId, e.getMessage());
            throw new DownstreamServiceException("knowledge-service", e.getStatusCode().value(), e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error connecting to Knowledge Service for document {}: {}", documentId,
                    e.getMessage());
            throw DownstreamServiceException.fromResourceAccessException("knowledge-service", e);
        } catch (Exception e) {
            if (e instanceof DownstreamServiceException) {
                throw (DownstreamServiceException) e;
            }
            log.error("Unexpected error retrieving document {} from Knowledge Service", documentId, e);
            throw new DownstreamServiceException("knowledge-service", "Unexpected failure retrieving document", e);
        }
    }

    @Override
    public boolean verifyDocumentWriteAccess(UUID documentId, UUID userId) {
        log.debug("Calling Knowledge Service GET /api/v1/documents/{}/permissions/verify for user {}", documentId,
                userId);
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/documents/{id}/permissions/verify")
                            .queryParam("userId", userId)
                            .queryParam("permission", "WRITE")
                            .build(documentId))
                    .header("X-Internal-Secret", internalSecret)
                    .exchange((req, res) -> {
                        int code = res.getStatusCode().value();
                        if (code == 404) {
                            throw new DocumentNotFoundException(documentId);
                        }
                        if (code == 403) {
                            log.warn("Access forbidden by Knowledge Service for document {} and user {}", documentId,
                                    userId);
                            return false;
                        }
                        if (res.getStatusCode().is5xxServerError()) {
                            log.error("Knowledge Service returned 5xx verifying document access {}: HTTP {}",
                                    documentId, code);
                            throw new DownstreamServiceException("knowledge-service", code,
                                    "Knowledge Service server error: " + res.getStatusText());
                        }
                        if (res.getStatusCode().is4xxClientError()) {
                            throw new DownstreamServiceException("knowledge-service", code,
                                    "Client error verifying document access: " + res.getStatusText());
                        }
                        try {
                            byte[] body = res.getBody().readAllBytes();
                            if (body.length == 0) {
                                return false;
                            }
                            DocumentPermissionVerificationResponse response = objectMapper.readValue(body,
                                    DocumentPermissionVerificationResponse.class);
                            return response != null && response.authorized();
                        } catch (Exception e) {
                            log.error("Failed to parse DocumentPermissionVerificationResponse JSON: {}",
                                    e.getMessage());
                            throw new DownstreamServiceException("knowledge-service",
                                    "Malformed permission response payload", e);
                        }
                    });
        } catch (DocumentNotFoundException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new DocumentNotFoundException(documentId);
        } catch (HttpClientErrorException.Forbidden e) {
            return false;
        } catch (HttpServerErrorException e) {
            log.error("Knowledge Service returned 5xx verifying document access {}: {}", documentId, e.getMessage());
            throw new DownstreamServiceException("knowledge-service", e.getStatusCode().value(), e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error connecting to Knowledge Service for document {}: {}", documentId,
                    e.getMessage());
            throw DownstreamServiceException.fromResourceAccessException("knowledge-service", e);
        } catch (Exception e) {
            if (e instanceof DownstreamServiceException) {
                throw (DownstreamServiceException) e;
            }
            log.error("Unexpected error verifying document {} access from Knowledge Service", documentId, e);
            throw new DownstreamServiceException("knowledge-service", "Unexpected failure verifying document access",
                    e);
        }
    }

    @Override
    public <T> T streamDocumentContent(UUID documentId, DocumentContentCallback<T> callback) {
        log.info("Streaming content for document {} from Knowledge Service", documentId);
        try {
            return restClient.get()
                    .uri("/api/v1/documents/{id}/content", documentId)
                    .header("X-Internal-Secret", internalSecret)
                    .exchange((req, res) -> {
                        int code = res.getStatusCode().value();
                        if (code == 404) {
                            log.warn("Document or content not found in Knowledge Service (HTTP 404) for document {}",
                                    documentId);
                            throw new DocumentNotFoundException(
                                    "Document or storage content not found in Knowledge Service: " + documentId);
                        }
                        if (code == 403 || code == 401) {
                            log.error("Authentication/authorization failure accessing document content for {}: HTTP {}",
                                    documentId, code);
                            throw new DownstreamServiceException("knowledge-service", code,
                                    "Unauthorized access to document content");
                        }
                        if (res.getStatusCode().is5xxServerError()) {
                            log.error("Knowledge Service returned 5xx streaming document {}: HTTP {}", documentId,
                                    code);
                            throw new DownstreamServiceException("knowledge-service", code,
                                    "Knowledge Service error streaming document: " + res.getStatusText());
                        }
                        if (res.getStatusCode().is4xxClientError()) {
                            throw new DownstreamServiceException("knowledge-service", code,
                                    "Client error from Knowledge Service: " + res.getStatusText());
                        }

                        String contentType = res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                        String contentDisposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
                        String filename = extractFilename(contentDisposition, "document");
                        long contentLength = res.getHeaders().getContentLength();

                        if (maxFileSizeBytes > 0 && contentLength > 0 && contentLength > maxFileSizeBytes) {
                            log.error("Document {} size ({} bytes) exceeds max limit ({} bytes)", documentId,
                                    contentLength, maxFileSizeBytes);
                            throw new PermanentProcessingException("FILE_TOO_LARGE",
                                    String.format(
                                            "Document file size %d bytes exceeds maximum allowed limit of %d bytes",
                                            contentLength, maxFileSizeBytes));
                        }

                        try (InputStream rawIs = res.getBody();
                             com.omragul.processing.util.BoundedInputStream boundedIs =
                                     new com.omragul.processing.util.BoundedInputStream(rawIs, maxFileSizeBytes, documentId.toString())) {
                            return callback.doWithContent(boundedIs, contentType, filename, contentLength);
                        } catch (PermanentProcessingException | DocumentNotFoundException | DownstreamServiceException e) {
                            throw e;
                        } catch (IOException e) {
                            log.error("I/O error streaming document content for {}: {}", documentId, e.getMessage());
                            throw new DownstreamServiceException("knowledge-service",
                                    "Interrupted stream reading document content", e);
                        }
                    });
        } catch (DocumentNotFoundException | PermanentProcessingException | DownstreamServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error streaming document content for {}: {}", documentId, e.getMessage());
            throw DownstreamServiceException.fromResourceAccessException("knowledge-service", e);
        } catch (Exception e) {
            if (e instanceof DownstreamServiceException) {
                throw (DownstreamServiceException) e;
            }
            log.error("Unexpected error streaming document {} from Knowledge Service", documentId, e);
            throw new DownstreamServiceException("knowledge-service", "Unexpected failure streaming document content",
                    e);
        }
    }

    private String extractFilename(String contentDisposition, String fallback) {
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            int idx = contentDisposition.indexOf("filename=");
            String fn = contentDisposition.substring(idx + 9).trim();
            if (fn.startsWith("\"") && fn.endsWith("\"") && fn.length() > 1) {
                return fn.substring(1, fn.length() - 1);
            }
            return fn;
        }
        return fallback;
    }

    @Override
    public void updateDocumentProcessingStatus(UUID documentId, String status) {
        log.info("Synchronizing document {} processing status to {} via Knowledge Service", documentId, status);
        try {
            restClient.patch()
                    .uri("/api/v1/documents/{id}/processing-status/{status}", documentId, status)
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully synchronized document {} status to {}", documentId, status);
        } catch (Exception e) {
            log.error("Failed to update processing status to {} for document {}: {}", status, documentId,
                    e.getMessage());
            // We log but don't fail the primary job if synchronization hits a transient
            // issue
        }
    }
}
