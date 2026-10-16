package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.DocumentMetadataRequest;
import com.omragul.knowledge.dto.DocumentMetadataResponse;
import com.omragul.knowledge.entity.DocumentMetadata;

import java.util.List;

public final class DocumentMetadataMapper {

    private DocumentMetadataMapper() {
    }

    public static DocumentMetadata toEntity(
            DocumentMetadataRequest request
    ) {

        if (request == null) {
            return null;
        }

        DocumentMetadata entity =
                new DocumentMetadata();

        entity.setDocumentId(
                request.getDocumentId()
        );

        entity.setMetadataKey(
                request.getMetadataKey()
        );

        entity.setMetadataValue(
                request.getMetadataValue()
        );

        entity.setValueType(
                request.getValueType()
        );

        return entity;
    }

    public static DocumentMetadataResponse toResponse(
            DocumentMetadata entity
    ) {

        if (entity == null) {
            return null;
        }

        DocumentMetadataResponse response =
                new DocumentMetadataResponse();

        response.setId(entity.getId());
        response.setDocumentId(entity.getDocumentId());
        response.setMetadataKey(entity.getMetadataKey());
        response.setMetadataValue(entity.getMetadataValue());
        response.setValueType(entity.getValueType());
        response.setCreatedAt(entity.getCreatedAt());
        response.setCreatedBy(entity.getCreatedBy());

        return response;
    }

    public static List<DocumentMetadataResponse> toResponseList(
            List<DocumentMetadata> entities
    ) {

        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(DocumentMetadataMapper::toResponse)
                .toList();
    }
}