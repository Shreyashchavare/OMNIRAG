package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.TagResponse;
import com.omragul.knowledge.entity.Tag;

import java.util.List;

public final class TagMapper {

    private TagMapper() {
    }

    public static TagResponse toResponse(
            Tag entity
    ) {
        if (entity == null) {
            return null;
        }

        TagResponse response =
                new TagResponse();

        response.setId(entity.getId());
        response.setKnowledgeBaseId(
                entity.getKnowledgeBaseId()
        );
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setColor(entity.getColor());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setCreatedBy(entity.getCreatedBy());
        response.setUpdatedBy(entity.getUpdatedBy());

        return response;
    }

    public static List<TagResponse> toResponseList(
            List<Tag> entities
    ) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(TagMapper::toResponse)
                .toList();
    }
}