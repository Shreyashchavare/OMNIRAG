package com.omragul.processing.client.impl;

import com.omragul.processing.client.EmbeddingServiceClient;
import com.omragul.processing.client.dto.BatchEmbeddingRequestDto;
import com.omragul.processing.client.dto.BatchEmbeddingResponseDto;
import com.omragul.processing.exception.DownstreamServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class EmbeddingServiceClientImpl implements EmbeddingServiceClient {

    private final RestClient restClient;

    public EmbeddingServiceClientImpl(
            @Value("${processing.embedding-service.url:http://localhost:8003}") String baseUrl,
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
    public BatchEmbeddingResponseDto generateBatchEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return BatchEmbeddingResponseDto.builder().build();
        }

        log.info("Calling Embedding Service POST /api/v1/embeddings/batch for {} texts", texts.size());
        try {
            BatchEmbeddingResponseDto response = restClient.post()
                    .uri("/api/v1/embeddings/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new BatchEmbeddingRequestDto(texts))
                    .retrieve()
                    .body(BatchEmbeddingResponseDto.class);

            if (response == null) {
                throw new DownstreamServiceException("embedding-service", 502, "Null response from embedding service");
            }
            return response;
        } catch (DownstreamServiceException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Client error from Embedding Service: {} - {}", e.getStatusCode(), e.getMessage());
            throw new DownstreamServiceException("embedding-service", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("Server error from Embedding Service: {} - {}", e.getStatusCode(), e.getMessage());
            throw new DownstreamServiceException("embedding-service", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.error("Network/timeout error connecting to Embedding Service: {}", e.getMessage());
            throw new DownstreamServiceException("embedding-service", "Network failure connecting to Embedding Service", e);
        } catch (Exception e) {
            log.error("Error generating embeddings: {}", e.getMessage());
            throw new DownstreamServiceException("embedding-service", "Failed to generate embeddings", e);
        }
    }
}
