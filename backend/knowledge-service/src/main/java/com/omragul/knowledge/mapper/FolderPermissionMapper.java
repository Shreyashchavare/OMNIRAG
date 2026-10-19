package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.FolderPermissionResponse;
import com.omragul.knowledge.entity.FolderPermission;

import java.util.List;

public final class FolderPermissionMapper {

    private FolderPermissionMapper() {
    }

    public static FolderPermissionResponse toResponse(
            FolderPermission entity
    ) {
        if (entity == null) {
            return null;
        }

        FolderPermissionResponse response =
                new FolderPermissionResponse();

        response.setId(entity.getId());
        response.setFolderId(entity.getFolderId());
        response.setPrincipalType(entity.getPrincipalType());
        response.setPrincipalId(entity.getPrincipalId());
        response.setPermission(entity.getPermission());
        response.setGrantedBy(entity.getGrantedBy());
        response.setGrantedAt(entity.getGrantedAt());
        response.setExpiresAt(entity.getExpiresAt());

        return response;
    }

    public static List<FolderPermissionResponse> toResponseList(
            List<FolderPermission> entities
    ) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(FolderPermissionMapper::toResponse)
                .toList();
    }
}