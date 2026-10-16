package com.omragul.knowledge.mapper;

import com.omragul.knowledge.dto.KnowledgeBaseResponse;
import com.omragul.knowledge.dto.KnowledgeBaseStatisticsResponse;
import com.omragul.knowledge.entity.KnowledgeBase;

import java.util.List;

public final class KnowledgeBaseMapper {

    private KnowledgeBaseMapper() {
    }

    public static KnowledgeBaseResponse toResponse(
            KnowledgeBase entity
    ) {

        if (entity == null) {
            return null;
        }

        KnowledgeBaseResponse response =
                new KnowledgeBaseResponse();

        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setOwnerId(entity.getOwnerId());

        response.setStatus(
                entity.getStatus() != null
                        ? entity.getStatus().name()
                        : null
        );

        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setCreatedBy(entity.getCreatedBy());
        response.setUpdatedBy(entity.getUpdatedBy());

        return response;
    }

    public static List<KnowledgeBaseResponse> toResponseList(
            List<KnowledgeBase> entities
    ) {

        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(KnowledgeBaseMapper::toResponse)
                .toList();
    }

    public static KnowledgeBaseStatisticsResponse toStatisticsResponse(
            KnowledgeBase entity
    ) {

        if (entity == null) {
            return null;
        }

        KnowledgeBaseStatisticsResponse response =
                new KnowledgeBaseStatisticsResponse();

        response.setKnowledgeBaseId(entity.getId());
        response.setName(entity.getName());

        response.setStatus(
                entity.getStatus() != null
                        ? entity.getStatus().name()
                        : null
        );

        response.setTotalDocuments(
                entity.getTotalDocuments()
        );

        response.setTotalSizeBytes(
                entity.getTotalSizeBytes()
        );

        return response;
    }
}