package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.DocumentVersion;

import java.util.List;
import java.util.UUID;

public interface DocumentVersionService {

    DocumentVersion create(DocumentVersion documentVersion);

    DocumentVersion getById(UUID versionId);

    DocumentVersion getVersion(
            UUID documentId,
            Integer versionNumber
    );

    DocumentVersion getLatestVersion(
            UUID documentId
    );

    List<DocumentVersion> getVersions(
            UUID documentId
    );

    DocumentVersion getByChecksum(
            String checksum
    );

    boolean existsByChecksum(
            String checksum
    );

    boolean existsVersion(
            UUID documentId,
            Integer versionNumber
    );

    int getNextVersionNumber(
            UUID documentId
    );

    long countVersions(
            UUID documentId
    );
}