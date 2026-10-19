package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.DocumentVersionRequest;
import com.omragul.knowledge.dto.DocumentVersionResponse;
import com.omragul.knowledge.entity.DocumentVersion;

import java.util.List;

public final class DocumentVersionMapper {

    private DocumentVersionMapper() {
    }

    // =========================================================
    // DocumentVersionRequest -> DocumentVersion
    // =========================================================

    public static DocumentVersion toEntity(
            DocumentVersionRequest request
    ) {

        if (request == null) {
            return null;
        }

        DocumentVersion entity = new DocumentVersion();

        entity.setDocumentId(request.getDocumentId());
        entity.setVersionNumber(request.getVersionNumber());

        entity.setOriginalFilename(
                request.getOriginalFilename()
        );

        entity.setStoredFilename(
                request.getStoredFilename()
        );

        entity.setStoragePath(
                request.getStoragePath()
        );

        entity.setStorageProvider(
                request.getStorageProvider()
        );

        entity.setFileExtension(
                request.getFileExtension()
        );

        entity.setMimeType(
                request.getMimeType()
        );

        entity.setFileSizeBytes(
                request.getFileSizeBytes()
        );

        entity.setChecksum(
                request.getChecksum()
        );

        entity.setUploadComment(
                request.getUploadComment()
        );

        entity.setUploadedBy(
                request.getUploadedBy()
        );

        return entity;
    }

    // =========================================================
    // DocumentVersion -> DocumentVersionResponse
    // =========================================================

    public static DocumentVersionResponse toResponse(
            DocumentVersion entity
    ) {

        if (entity == null) {
            return null;
        }

        DocumentVersionResponse response =
                new DocumentVersionResponse();

        response.setId(entity.getId());
        response.setDocumentId(entity.getDocumentId());
        response.setVersionNumber(entity.getVersionNumber());

        response.setOriginalFilename(
                entity.getOriginalFilename()
        );

        response.setStoredFilename(
                entity.getStoredFilename()
        );

        response.setStoragePath(
                entity.getStoragePath()
        );

        response.setStorageProvider(
                entity.getStorageProvider() != null
                        ? entity.getStorageProvider().name()
                        : null
        );

        response.setFileExtension(
                entity.getFileExtension()
        );

        response.setMimeType(
                entity.getMimeType()
        );

        response.setFileSizeBytes(
                entity.getFileSizeBytes()
        );

        response.setChecksum(
                entity.getChecksum()
        );

        response.setUploadComment(
                entity.getUploadComment()
        );

        response.setUploadedBy(
                entity.getUploadedBy()
        );

        response.setCreatedAt(
                entity.getCreatedAt()
        );

        return response;
    }

    // =========================================================
    // List<DocumentVersion> -> List<DocumentVersionResponse>
    // =========================================================

    public static List<DocumentVersionResponse> toResponseList(
            List<DocumentVersion> entities
    ) {

        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(DocumentVersionMapper::toResponse)
                .toList();
    }
}