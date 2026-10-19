package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByKnowledgeBaseId(UUID knowledgeBaseId);

    List<Category> findByKnowledgeBaseIdAndStatus(
            UUID knowledgeBaseId,
            Category.Status status
    );

    List<Category> findByParentCategoryId(UUID parentCategoryId);

    List<Category> findByKnowledgeBaseIdAndParentCategoryId(
            UUID knowledgeBaseId,
            UUID parentCategoryId
    );

    List<Category> findByKnowledgeBaseIdAndParentCategoryIdIsNull(
            UUID knowledgeBaseId
    );

    Optional<Category> findByKnowledgeBaseIdAndName(
            UUID knowledgeBaseId,
            String name
    );

    boolean existsByKnowledgeBaseIdAndName(
            UUID knowledgeBaseId,
            String name
    );
}