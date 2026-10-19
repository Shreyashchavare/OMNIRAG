package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.DocumentPermissionResponse;
import com.omragul.knowledge.entity.DocumentPermission;

import java.util.List;

public final class DocumentPermissionMapper {

    private DocumentPermissionMapper() {
    }

    public static DocumentPermissionResponse toResponse(
            DocumentPermission entity
    ) {
        if (entity == null) {
            return null;
        }

        DocumentPermissionResponse response =
                new DocumentPermissionResponse();

        response.setId(entity.getId());
        response.setDocumentId(entity.getDocumentId());
        response.setPrincipalType(entity.getPrincipalType());
        response.setPrincipalId(entity.getPrincipalId());
        response.setPermission(entity.getPermission());
        response.setGrantedBy(entity.getGrantedBy());
        response.setGrantedAt(entity.getGrantedAt());
        response.setExpiresAt(entity.getExpiresAt());

        return response;
    }

    public static List<DocumentPermissionResponse> toResponseList(
            List<DocumentPermission> entities
    ) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(DocumentPermissionMapper::toResponse)
                .toList();
    }
}