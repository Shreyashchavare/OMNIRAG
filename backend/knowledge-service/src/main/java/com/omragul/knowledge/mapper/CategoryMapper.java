package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.CategoryResponse;
import com.omragul.knowledge.entity.Category;

import java.util.List;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponse toResponse(
            Category entity
    ) {
        if (entity == null) {
            return null;
        }

        CategoryResponse response =
                new CategoryResponse();

        response.setId(entity.getId());
        response.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        response.setParentCategoryId(
                entity.getParentCategoryId()
        );
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setColor(entity.getColor());
        response.setIcon(entity.getIcon());

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

    public static List<CategoryResponse> toResponseList(
            List<Category> entities
    ) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }
}