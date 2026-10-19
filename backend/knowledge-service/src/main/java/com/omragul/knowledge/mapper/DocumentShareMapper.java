package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.DocumentShareResponse;
import com.omragul.knowledge.entity.DocumentShare;

import java.util.List;

public final class DocumentShareMapper {

    private DocumentShareMapper() {
    }

    public static DocumentShareResponse toResponse(
            DocumentShare entity
    ) {
        if (entity == null) {
            return null;
        }

        DocumentShareResponse response =
                new DocumentShareResponse();

        response.setId(entity.getId());
        response.setDocumentId(entity.getDocumentId());
        response.setSharedBy(entity.getSharedBy());
        response.setSharedWith(entity.getSharedWith());
        response.setAccessLevel(entity.getAccessLevel());
        response.setExpiresAt(entity.getExpiresAt());
        response.setCreatedAt(entity.getCreatedAt());

        return response;
    }

    public static List<DocumentShareResponse> toResponseList(
            List<DocumentShare> entities
    ) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(DocumentShareMapper::toResponse)
                .toList();
    }
}