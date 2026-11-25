package com.omragul.processing.client;

import com.omragul.processing.client.dto.DocumentDto;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeServiceClient {

    Optional<DocumentDto> getDocument(UUID documentId);

    boolean verifyDocumentWriteAccess(UUID documentId, UUID userId);

    void updateDocumentProcessingStatus(UUID documentId, String status);

    <T> T streamDocumentContent(UUID documentId, DocumentContentCallback<T> callback);
}
