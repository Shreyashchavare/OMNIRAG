package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DocumentService {

    Document create(Document document);

    Document getById(UUID documentId);

    Document getByKnowledgeBaseAndId(
            UUID knowledgeBaseId,
            UUID documentId
    );

    List<Document> getByKnowledgeBase(
            UUID knowledgeBaseId
    );

    Page<Document> getByKnowledgeBase(
            UUID knowledgeBaseId,
            Pageable pageable
    );

    List<Document> getByKnowledgeBaseAndStatus(
            UUID knowledgeBaseId,
            Document.Status status
    );

    Page<Document> getByKnowledgeBaseAndStatus(
            UUID knowledgeBaseId,
            Document.Status status,
            Pageable pageable
    );

    List<Document> getByProcessingStatus(
            UUID knowledgeBaseId,
            Document.ProcessingStatus processingStatus
    );

    List<Document> getByFolder(
            UUID folderId
    );

    Page<Document> getByFolder(
            UUID folderId,
            Pageable pageable
    );

    List<Document> getByCategory(
            UUID categoryId
    );

    Page<Document> getByCategory(
            UUID categoryId,
            Pageable pageable
    );

    List<Document> getByOwner(
            UUID ownerId
    );

    Document update(
            UUID documentId,
            Document document
    );

    Document updateProcessingStatus(
            UUID documentId,
            Document.ProcessingStatus processingStatus
    );

    Document archive(UUID documentId);

    Document restore(UUID documentId);

    Document softDelete(UUID documentId);

    boolean existsByChecksum(String checksum);

    boolean existsByKnowledgeBaseAndName(
            UUID knowledgeBaseId,
            String name
    );

    long countActiveDocuments(UUID knowledgeBaseId);

    long countActiveDocumentsInFolder(UUID folderId);

    long countActiveDocumentsInCategory(UUID categoryId);
}