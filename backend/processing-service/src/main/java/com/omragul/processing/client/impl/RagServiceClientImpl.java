package com.omragul.processing.client.impl;

import com.omragul.processing.client.RagServiceClient;
import com.omragul.processing.client.dto.RagIndexRequestDto;
import com.omragul.processing.client.dto.RagIndexResponseDto;
import com.omragul.processing.exception.DownstreamServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final String internalSecret;

    @Autowired
    public RagServiceClientImpl(
            @Value("${processing.rag-service.url:http://localhost:8002}") String baseUrl,
            @Value("${processing.orchestration.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${processing.rag-service.read-timeout-ms:${processing.orchestration.ai-read-timeout-ms:30000}}") int readTimeoutMs,
            @Value("${internal.service.secret:}") String internalSecret
    ) {
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public RagServiceClientImpl(
            @Value("${processing.rag-service.url:http://localhost:8002}") String baseUrl,
            @Value("${processing.orchestration.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${processing.rag-service.read-timeout-ms:${processing.orchestration.ai-read-timeout-ms:30000}}") int readTimeoutMs
    ) {
        this(baseUrl, connectTimeoutMs, readTimeoutMs, "");
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
            RagIndexRequestDto requestDto = new RagIndexRequestDto(
                    chunkPayload,
                    knowledgeBaseId != null ? knowledgeBaseId.toString() : null
            );
            RagIndexResponseDto response = restClient.post()
                    .uri("/api/v1/rag/index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Secret", internalSecret)
                    .header("X-Internal-Service-Secret", internalSecret)
                    .header("X-Internal-Caller-Service", "processing-service")
                    .body(requestDto)
                    .retrieve()
                    .body(RagIndexResponseDto.class);

            int indexed = response != null ? response.getIndexed() : chunks.size();
            log.info("Successfully indexed {} chunks for document: {}", indexed, documentId);
            return indexed;
        } catch (DownstreamServiceException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Client error from RAG Service: {} - {}", e.getStatusCode(), e.getMessage());
            throw DownstreamServiceException.fromHttpClientErrorException("rag-service", e);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("Server error from RAG Service: {} - {}", e.getStatusCode(), e.getMessage());
            throw DownstreamServiceException.fromHttpServerErrorException("rag-service", e);
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error connecting to RAG Service: {}", e.getMessage());
            throw DownstreamServiceException.fromResourceAccessException("rag-service", e);
        } catch (Exception e) {
            log.error("Error indexing chunks in RAG Service: {}", e.getMessage());
            throw DownstreamServiceException.fromMalformedResponse("rag-service", "Failed to index chunks in vector store", e);
        }
    }
}
