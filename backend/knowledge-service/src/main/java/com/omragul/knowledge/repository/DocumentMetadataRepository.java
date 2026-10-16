package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentMetadataRepository
        extends JpaRepository<DocumentMetadata, UUID> {

    List<DocumentMetadata> findByDocumentId(
            UUID documentId
    );

    Optional<DocumentMetadata> findByDocumentIdAndMetadataKey(
            UUID documentId,
            String metadataKey
    );

    boolean existsByDocumentIdAndMetadataKey(
            UUID documentId,
            String metadataKey
    );

    void deleteByDocumentIdAndMetadataKey(
            UUID documentId,
            String metadataKey
    );
}