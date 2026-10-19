package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.FolderResponse;
import com.omragul.knowledge.entity.Folder;

import java.util.List;

public final class FolderMapper {

    private FolderMapper() {
    }

    public static FolderResponse toResponse(
            Folder entity
    ) {
        if (entity == null) {
            return null;
        }

        FolderResponse response =
                new FolderResponse();

        response.setId(entity.getId());
        response.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        response.setParentFolderId(entity.getParentFolderId());
        response.setName(entity.getName());
        response.setPath(entity.getPath());
        response.setLevel(entity.getLevel());
        response.setOwnerId(entity.getOwnerId());

        response.setStatus(
                entity.getStatus() != null
                        ? entity.getStatus().name()
                        : null
        );

        response.setTotalDocuments(
                entity.getTotalDocuments()
        );

        response.setTotalSubfolders(
                entity.getTotalSubfolders()
        );

        response.setTotalSizeBytes(
                entity.getTotalSizeBytes()
        );

        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setCreatedBy(entity.getCreatedBy());
        response.setUpdatedBy(entity.getUpdatedBy());
        response.setDeletedAt(entity.getDeletedAt());
        response.setVersion(entity.getVersion());

        return response;
    }

    public static List<FolderResponse> toResponseList(
            List<Folder> entities
    ) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(FolderMapper::toResponse)
                .toList();
    }
}