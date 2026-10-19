package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeBaseRepository
        extends JpaRepository<KnowledgeBase, UUID> {

    Optional<KnowledgeBase> findBySlug(String slug);

    Optional<KnowledgeBase> findBySlugAndStatus(
            String slug,
            KnowledgeBase.Status status
    );

    boolean existsBySlug(String slug);

    boolean existsByNameAndOwnerId(
            String name,
            UUID ownerId
    );

    List<KnowledgeBase> findByOwnerId(
            UUID ownerId
    );

    List<KnowledgeBase> findByOwnerIdAndStatus(
            UUID ownerId,
            KnowledgeBase.Status status
    );
}