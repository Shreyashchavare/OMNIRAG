package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.DocumentMetadata;

import java.util.List;
import java.util.UUID;

public interface DocumentMetadataService {

    DocumentMetadata create(
            DocumentMetadata metadata
    );

    DocumentMetadata update(
            DocumentMetadata metadata
    );

    DocumentMetadata getById(
            UUID metadataId
    );

    List<DocumentMetadata> getByDocumentId(
            UUID documentId
    );

    DocumentMetadata getByDocumentIdAndKey(
            UUID documentId,
            String metadataKey
    );

    boolean existsByDocumentIdAndKey(
            UUID documentId,
            String metadataKey
    );

    DocumentMetadata upsert(
            DocumentMetadata metadata
    );

    void delete(
            UUID metadataId
    );

    void deleteByDocumentIdAndKey(
            UUID documentId,
            String metadataKey
    );
}