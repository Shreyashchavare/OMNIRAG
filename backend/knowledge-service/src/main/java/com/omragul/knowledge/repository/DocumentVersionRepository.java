package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository
        extends JpaRepository<DocumentVersion, UUID> {

    // All versions of a document, newest first
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(
            UUID documentId
    );

    // Specific version
    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(
            UUID documentId,
            Integer versionNumber
    );

    // Latest version
    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(
            UUID documentId
    );

    // Check whether a version already exists
    boolean existsByDocumentIdAndVersionNumber(
            UUID documentId,
            Integer versionNumber
    );

    // Find version by file checksum
    Optional<DocumentVersion> findByChecksum(
            String checksum
    );

    // Check duplicate file/version content
    boolean existsByChecksum(
            String checksum
    );

    // Number of versions belonging to a document
    long countByDocumentId(
            UUID documentId
    );
}