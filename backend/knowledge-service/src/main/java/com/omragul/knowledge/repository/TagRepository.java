package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByKnowledgeBaseId(UUID knowledgeBaseId);

    Optional<Tag> findByKnowledgeBaseIdAndName(
            UUID knowledgeBaseId,
            String name
    );

    boolean existsByKnowledgeBaseIdAndName(
            UUID knowledgeBaseId,
            String name
    );
}