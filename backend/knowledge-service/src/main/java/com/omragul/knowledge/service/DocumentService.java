package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DocumentService {

    Document create(Document document, UUID userId);

    default Document create(Document document) {
        return create(document, document != null ? document.getOwnerId() : null);
    }

    Document getById(UUID documentId, UUID userId, boolean isInternalService);

    default Document getById(UUID documentId, UUID userId) {
        return getById(documentId, userId, false);
    }

    default Document getById(UUID documentId) {
        return getById(documentId, null, false);
    }

    Document getByKnowledgeBaseAndId(
            UUID knowledgeBaseId,
            UUID documentId,
            UUID userId
    );

    default Document getByKnowledgeBaseAndId(
            UUID knowledgeBaseId,
            UUID documentId
    ) {
        return getByKnowledgeBaseAndId(knowledgeBaseId, documentId, null);
    }

    List<Document> getByKnowledgeBase(
            UUID knowledgeBaseId,
            UUID userId
    );

    default List<Document> getByKnowledgeBase(
            UUID knowledgeBaseId
    ) {
        return getByKnowledgeBase(knowledgeBaseId, (UUID) null);
    }

    Page<Document> getByKnowledgeBase(
            UUID knowledgeBaseId,
            UUID userId,
            Pageable pageable
    );

    default Page<Document> getByKnowledgeBase(
            UUID knowledgeBaseId,
            Pageable pageable
    ) {
        return getByKnowledgeBase(knowledgeBaseId, null, pageable);
    }

    List<Document> getByKnowledgeBaseAndStatus(
            UUID knowledgeBaseId,
            Document.Status status,
            UUID userId
    );

    default List<Document> getByKnowledgeBaseAndStatus(
            UUID knowledgeBaseId,
            Document.Status status
    ) {
        return getByKnowledgeBaseAndStatus(knowledgeBaseId, status, (UUID) null);
    }

    Page<Document> getByKnowledgeBaseAndStatus(
            UUID knowledgeBaseId,
            Document.Status status,
            UUID userId,
            Pageable pageable
    );

    default Page<Document> getByKnowledgeBaseAndStatus(
            UUID knowledgeBaseId,
            Document.Status status,
            Pageable pageable
    ) {
        return getByKnowledgeBaseAndStatus(knowledgeBaseId, status, null, pageable);
    }

    List<Document> getByProcessingStatus(
            UUID knowledgeBaseId,
            Document.ProcessingStatus processingStatus,
            UUID userId
    );

    default List<Document> getByProcessingStatus(
            UUID knowledgeBaseId,
            Document.ProcessingStatus processingStatus
    ) {
        return getByProcessingStatus(knowledgeBaseId, processingStatus, null);
    }

    List<Document> getByFolder(
            UUID folderId,
            UUID userId
    );

    default List<Document> getByFolder(
            UUID folderId
    ) {
        return getByFolder(folderId, (UUID) null);
    }

    Page<Document> getByFolder(
            UUID folderId,
            UUID userId,
            Pageable pageable
    );

    default Page<Document> getByFolder(
            UUID folderId,
            Pageable pageable
    ) {
        return getByFolder(folderId, null, pageable);
    }

    List<Document> getByCategory(
            UUID categoryId,
            UUID userId
    );

    default List<Document> getByCategory(
            UUID categoryId
    ) {
        return getByCategory(categoryId, (UUID) null);
    }

    Page<Document> getByCategory(
            UUID categoryId,
            UUID userId,
            Pageable pageable
    );

    default Page<Document> getByCategory(
            UUID categoryId,
            Pageable pageable
    ) {
        return getByCategory(categoryId, null, pageable);
    }

    List<Document> getByOwner(
            UUID ownerId,
            UUID userId
    );

    default List<Document> getByOwner(
            UUID ownerId
    ) {
        return getByOwner(ownerId, null);
    }

    Document update(
            UUID documentId,
            Document document,
            UUID userId
    );

    default Document update(
            UUID documentId,
            Document document
    ) {
        return update(documentId, document, document != null ? document.getOwnerId() : null);
    }

    Document updateProcessingStatus(
            UUID documentId,
            Document.ProcessingStatus processingStatus
    );

    Document archive(UUID documentId, UUID userId);

    default Document archive(UUID documentId) {
        return archive(documentId, null);
    }

    Document restore(UUID documentId, UUID userId);

    default Document restore(UUID documentId) {
        return restore(documentId, null);
    }

    Document softDelete(UUID documentId, UUID userId);

    default Document softDelete(UUID documentId) {
        return softDelete(documentId, null);
    }

    boolean existsByChecksum(String checksum);

    boolean existsByKnowledgeBaseAndName(
            UUID knowledgeBaseId,
            String name
    );

    long countActiveDocuments(UUID knowledgeBaseId);

    long countActiveDocumentsInFolder(UUID folderId);

    long countActiveDocumentsInCategory(UUID categoryId);
}
