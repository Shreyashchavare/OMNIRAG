package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.DocumentRequest;
import com.omragul.knowledge.dto.DocumentResponse;
import com.omragul.knowledge.dto.DocumentUpdateRequest;
import com.omragul.knowledge.entity.Document;

import java.util.List;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    // =========================================================
    // DocumentRequest -> Document
    // =========================================================

    public static Document toEntity(DocumentRequest request) {

        if (request == null) {
            return null;
        }

        Document entity = new Document();

        entity.setKnowledgeBaseId(request.getKnowledgeBaseId());
        entity.setFolderId(request.getFolderId());
        entity.setCategoryId(request.getCategoryId());

        entity.setName(request.getName());
        entity.setOriginalFilename(request.getOriginalFilename());
        entity.setStoredFilename(request.getStoredFilename());
        entity.setStoragePath(request.getStoragePath());
        entity.setFileExtension(request.getFileExtension());
        entity.setMimeType(request.getMimeType());

        entity.setFileSizeBytes(request.getFileSizeBytes());
        entity.setChecksum(request.getChecksum());

        entity.setOwnerId(request.getOwnerId());

        return entity;
    }

    // =========================================================
    // DocumentUpdateRequest -> existing Document
    // =========================================================

    public static void updateEntity(
            Document entity,
            DocumentUpdateRequest request
    ) {

        if (entity == null || request == null) {
            return;
        }

        entity.setFolderId(request.getFolderId());
        entity.setCategoryId(request.getCategoryId());

        entity.setName(request.getName());
        entity.setOriginalFilename(request.getOriginalFilename());
        entity.setStoredFilename(request.getStoredFilename());
        entity.setStoragePath(request.getStoragePath());
        entity.setFileExtension(request.getFileExtension());
        entity.setMimeType(request.getMimeType());

        entity.setFileSizeBytes(request.getFileSizeBytes());
        entity.setChecksum(request.getChecksum());
    }

    // =========================================================
    // Document -> DocumentResponse
    // =========================================================

    public static DocumentResponse toResponse(Document entity) {

        if (entity == null) {
            return null;
        }

        DocumentResponse response = new DocumentResponse();

        response.setId(entity.getId());
        response.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        response.setFolderId(entity.getFolderId());
        response.setCategoryId(entity.getCategoryId());

        response.setName(entity.getName());
        response.setOriginalFilename(entity.getOriginalFilename());
        response.setStoredFilename(entity.getStoredFilename());
        response.setStoragePath(entity.getStoragePath());

        response.setFileExtension(entity.getFileExtension());
        response.setMimeType(entity.getMimeType());
        response.setFileSizeBytes(entity.getFileSizeBytes());
        response.setChecksum(entity.getChecksum());

        response.setOwnerId(entity.getOwnerId());

        response.setCurrentVersion(entity.getCurrentVersion());

        response.setProcessingStatus(
                entity.getProcessingStatus() != null
                        ? entity.getProcessingStatus().name()
                        : null
        );

        response.setStatus(
                entity.getStatus() != null
                        ? entity.getStatus().name()
                        : null
        );

        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        response.setCreatedBy(entity.getCreatedBy());
        response.setUpdatedBy(entity.getUpdatedBy());

        response.setDeletedAt(entity.getDeletedAt());

        response.setVersion(entity.getVersion());

        return response;
    }

    // =========================================================
    // List<Document> -> List<DocumentResponse>
    // =========================================================

    public static List<DocumentResponse> toResponseList(
            List<Document> entities
    ) {

        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(DocumentMapper::toResponse)
                .toList();
    }
}