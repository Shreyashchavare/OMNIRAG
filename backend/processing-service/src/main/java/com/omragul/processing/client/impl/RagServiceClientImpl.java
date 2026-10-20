package com.omragul.processing.client.impl;

import com.omragul.processing.client.RagServiceClient;
import com.omragul.processing.client.dto.RagIndexRequestDto;
import com.omragul.processing.client.dto.RagIndexResponseDto;
import com.omragul.processing.exception.DownstreamServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class RagServiceClientImpl implements RagServiceClient {

    private final RestClient restClient;

    public RagServiceClientImpl(
            @Value("${processing.rag-service.url:http://localhost:8002}") String baseUrl,
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
    public int indexChunks(UUID documentId, UUID knowledgeBaseId, List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        log.info("Calling RAG Service POST /api/v1/rag/index for document: {} with {} chunks", documentId, chunks.size());

        List<Map<String, Object>> chunkPayload = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunkMap = new HashMap<>();
            chunkMap.put("text", chunks.get(i));
            chunkMap.put("document_id", documentId.toString());
            chunkMap.put("chunk_index", i);
            if (knowledgeBaseId != null) {
                chunkMap.put("knowledge_base_id", knowledgeBaseId.toString());
            }
            chunkPayload.add(chunkMap);
        }

        try {
            RagIndexResponseDto response = restClient.post()
                    .uri("/api/v1/rag/index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RagIndexRequestDto(chunkPayload))
                    .retrieve()
                    .body(RagIndexResponseDto.class);

            int indexed = response != null ? response.getIndexed() : chunks.size();
            log.info("Successfully indexed {} chunks for document: {}", indexed, documentId);
            return indexed;
        } catch (DownstreamServiceException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Client error from RAG Service: {} - {}", e.getStatusCode(), e.getMessage());
            throw new DownstreamServiceException("rag-service", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("Server error from RAG Service: {} - {}", e.getStatusCode(), e.getMessage());
            throw new DownstreamServiceException("rag-service", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error connecting to RAG Service: {}", e.getMessage());
            throw new DownstreamServiceException("rag-service", "Network failure connecting to RAG Service", e);
        } catch (Exception e) {
            log.error("Error indexing chunks in RAG Service: {}", e.getMessage());
            throw new DownstreamServiceException("rag-service", "Failed to index chunks in vector store", e);
        }
    }
}
