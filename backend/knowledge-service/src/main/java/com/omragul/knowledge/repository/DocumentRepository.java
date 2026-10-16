package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    // ---------- Knowledge Base ----------

    List<Document> findByKnowledgeBaseId(
            UUID knowledgeBaseId
    );

    Page<Document> findByKnowledgeBaseId(
            UUID knowledgeBaseId,
            Pageable pageable
    );

    List<Document> findByKnowledgeBaseIdAndStatus(
            UUID knowledgeBaseId,
            Document.Status status
    );

    Page<Document> findByKnowledgeBaseIdAndStatus(
            UUID knowledgeBaseId,
            Document.Status status,
            Pageable pageable
    );

    // ---------- Processing ----------

    List<Document> findByKnowledgeBaseIdAndProcessingStatus(
            UUID knowledgeBaseId,
            Document.ProcessingStatus processingStatus
    );

    // ---------- Folder ----------

    List<Document> findByFolderId(
            UUID folderId
    );

    Page<Document> findByFolderId(
            UUID folderId,
            Pageable pageable
    );

    // ---------- Category ----------

    List<Document> findByCategoryId(
            UUID categoryId
    );

    Page<Document> findByCategoryId(
            UUID categoryId,
            Pageable pageable
    );

    // ---------- Owner ----------

    List<Document> findByOwnerId(
            UUID ownerId
    );

    // ---------- Lookup ----------

    Optional<Document> findByKnowledgeBaseIdAndId(
            UUID knowledgeBaseId,
            UUID documentId
    );

    Optional<Document> findByChecksum(
            String checksum
    );

    // ---------- Existence ----------

    boolean existsByChecksum(
            String checksum
    );

    boolean existsByKnowledgeBaseIdAndName(
            UUID knowledgeBaseId,
            String name
    );

    // ---------- Counts ----------

    long countByKnowledgeBaseIdAndStatus(
            UUID knowledgeBaseId,
            Document.Status status
    );

    long countByFolderIdAndStatus(
            UUID folderId,
            Document.Status status
    );

    long countByCategoryIdAndStatus(
            UUID categoryId,
            Document.Status status
    );
}