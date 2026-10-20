package com.omragul.processing.client.impl;

import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.exception.DownstreamServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class KnowledgeServiceClientImpl implements KnowledgeServiceClient {

    private final RestClient restClient;

    public KnowledgeServiceClientImpl(
            @Value("${processing.knowledge-service.url:http://localhost:8082}") String baseUrl,
            @Value("${processing.orchestration.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${processing.orchestration.read-timeout-ms:30000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Optional<DocumentDto> getDocument(UUID documentId) {
        log.debug("Calling Knowledge Service GET /api/v1/documents/{}", documentId);
        try {
            DocumentDto document = restClient.get()
                    .uri("/api/v1/documents/{id}", documentId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            log.warn("Document {} not found in Knowledge Service (HTTP 404)", documentId);
                        } else {
                            throw new DownstreamServiceException("knowledge-service", res.getStatusCode().value(),
                                    "Client error from Knowledge Service: " + res.getStatusText());
                        }
                    })
                    .body(DocumentDto.class);

            return Optional.ofNullable(document);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpServerErrorException e) {
            log.error("Knowledge Service returned 5xx for document {}: {}", documentId, e.getMessage());
            throw new DownstreamServiceException("knowledge-service", e.getStatusCode().value(), e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error connecting to Knowledge Service for document {}: {}", documentId, e.getMessage());
            throw new DownstreamServiceException("knowledge-service", "Network failure connecting to Knowledge Service", e);
        } catch (Exception e) {
            if (e instanceof DownstreamServiceException) {
                throw (DownstreamServiceException) e;
            }
            log.error("Unexpected error retrieving document {} from Knowledge Service", documentId, e);
            throw new DownstreamServiceException("knowledge-service", "Unexpected failure retrieving document", e);
        }
    }

    @Override
    public void updateDocumentProcessingStatus(UUID documentId, String status) {
        log.info("Synchronizing document {} processing status to {} via Knowledge Service", documentId, status);
        try {
            restClient.patch()
                    .uri("/api/v1/documents/{id}/processing-status/{status}", documentId, status)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully synchronized document {} status to {}", documentId, status);
        } catch (Exception e) {
            log.error("Failed to update processing status to {} for document {}: {}", status, documentId, e.getMessage());
            // We log but don't fail the primary job if synchronization hits a transient issue
        }
    }
}
