package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.DocumentTagResponse;
import com.omragul.knowledge.entity.DocumentTag;

import java.util.List;

public final class DocumentTagMapper {

    private DocumentTagMapper() {
    }

    public static DocumentTagResponse toResponse(
            DocumentTag entity
    ) {

        if (entity == null) {
            return null;
        }

        DocumentTagResponse response =
                new DocumentTagResponse();

        response.setDocumentId(entity.getDocumentId());
        response.setTagId(entity.getTagId());
        response.setTaggedBy(entity.getTaggedBy());
        response.setTaggedAt(entity.getTaggedAt());

        return response;
    }

    public static List<DocumentTagResponse> toResponseList(
            List<DocumentTag> entities
    ) {

        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(DocumentTagMapper::toResponse)
                .toList();
    }
}