package com.omragul.processing.client;

import com.omragul.processing.client.dto.DocumentDto;

import java.util.List;

public interface MultimodalServiceClient {

    String extractText(DocumentDto document);

    List<String> createChunks(String text);
}
