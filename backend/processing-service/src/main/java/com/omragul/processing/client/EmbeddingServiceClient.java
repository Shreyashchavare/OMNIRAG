package com.omragul.processing.client;

import com.omragul.processing.client.dto.BatchEmbeddingResponseDto;

import java.util.List;

public interface EmbeddingServiceClient {

    BatchEmbeddingResponseDto generateBatchEmbeddings(List<String> texts);
}
