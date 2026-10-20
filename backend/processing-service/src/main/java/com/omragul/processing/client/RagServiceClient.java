package com.omragul.processing.client;

import java.util.List;
import java.util.UUID;

public interface RagServiceClient {

    int indexChunks(UUID documentId, UUID knowledgeBaseId, List<String> chunks);
}
