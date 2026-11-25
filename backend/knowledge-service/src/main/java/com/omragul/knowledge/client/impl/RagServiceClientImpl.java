package com.omragul.knowledge.client.impl;

import com.omragul.knowledge.client.RagServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class RagServiceClientImpl implements RagServiceClient {

    private final RestClient restClient;
    private final String internalSecret;

    public RagServiceClientImpl(
            @Value("${knowledge.rag-service.url:${RAG_SERVICE_URL:http://localhost:8002}}") String baseUrl,
            @Value("${internal.service.secret:}") String internalSecret
    ) {
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(1000));
        factory.setReadTimeout(Duration.ofMillis(5000));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public void deleteDocumentVectors(UUID documentId, UUID knowledgeBaseId) {
        if (documentId == null) {
            return;
        }

        try {
            log.info("Notifying RAG Service to delete vectors for document: {} in KB: {}", documentId, knowledgeBaseId);
            restClient.delete()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/v1/rag/documents/{id}");
                        if (knowledgeBaseId != null) {
                            builder.queryParam("knowledge_base_id", knowledgeBaseId.toString());
                        }
                        return builder.build(documentId);
                    })
                    .header("X-Internal-Secret", internalSecret)
                    .header("X-Internal-Service-Secret", internalSecret)
                    .header("X-Internal-Caller-Service", "knowledge-service")
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully deleted RAG vectors for document: {}", documentId);
        } catch (Exception e) {
            log.warn("Failed to synchronize vector deletion for document {}: {}", documentId, e.getMessage());
        }
    }

    @Override
    public void deleteKnowledgeBaseVectors(UUID knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return;
        }

        try {
            log.info("Notifying RAG Service to purge vectors for KB: {}", knowledgeBaseId);
            restClient.delete()
                    .uri("/api/v1/rag/knowledge-bases/{id}", knowledgeBaseId)
                    .header("X-Internal-Secret", internalSecret)
                    .header("X-Internal-Service-Secret", internalSecret)
                    .header("X-Internal-Caller-Service", "knowledge-service")
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully purged RAG vectors for KB: {}", knowledgeBaseId);
        } catch (Exception e) {
            log.warn("Failed to synchronize vector purge for KB {}: {}", knowledgeBaseId, e.getMessage());
        }
    }
}
