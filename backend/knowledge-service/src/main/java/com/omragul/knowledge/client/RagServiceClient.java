package com.omragul.knowledge.client;

import java.util.UUID;

public interface RagServiceClient {

    void deleteDocumentVectors(UUID documentId, UUID knowledgeBaseId);

    void deleteKnowledgeBaseVectors(UUID knowledgeBaseId);
}
