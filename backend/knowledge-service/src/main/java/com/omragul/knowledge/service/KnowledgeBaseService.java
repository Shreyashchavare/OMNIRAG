package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseService(
            KnowledgeBaseRepository knowledgeBaseRepository
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    public KnowledgeBase create(
            KnowledgeBase knowledgeBase,
            UUID userId
    ) {

        if (knowledgeBase == null) {
            throw new IllegalArgumentException(
                    "Knowledge base cannot be null"
            );
        }

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        if (knowledgeBase.getName() == null
                || knowledgeBase.getName().isBlank()) {

            throw new IllegalArgumentException(
                    "Knowledge base name is required"
            );
        }

        if (knowledgeBase.getSlug() == null
                || knowledgeBase.getSlug().isBlank()) {

            throw new IllegalArgumentException(
                    "Knowledge base slug is required"
            );
        }

        if (knowledgeBaseRepository.existsBySlug(
                knowledgeBase.getSlug())) {

            throw new IllegalArgumentException(
                    "Knowledge base slug already exists"
            );
        }

        if (knowledgeBaseRepository.existsByNameAndOwnerId(
                knowledgeBase.getName(),
                userId)) {

            throw new IllegalArgumentException(
                    "Knowledge base with this name already exists"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        knowledgeBase.setOwnerId(userId);
        knowledgeBase.setCreatedBy(userId);
        knowledgeBase.setUpdatedBy(userId);

        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);

        knowledgeBase.setStatus(
                KnowledgeBase.Status.ACTIVE
        );

        knowledgeBase.setVersion(1);

        knowledgeBase.setTotalDocuments(0);
        knowledgeBase.setTotalSizeBytes(0L);

        return knowledgeBaseRepository.save(knowledgeBase);
    }

    @Transactional(readOnly = true)
    public KnowledgeBase getById(UUID id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Knowledge base ID cannot be null"
            );
        }

        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Knowledge base not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public KnowledgeBase getActiveById(UUID id) {

        KnowledgeBase knowledgeBase = getById(id);

        if (knowledgeBase.getStatus()
                != KnowledgeBase.Status.ACTIVE) {

            throw new IllegalArgumentException(
                    "Knowledge base is not active"
            );
        }

        return knowledgeBase;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBase> getByOwner(UUID ownerId) {

        if (ownerId == null) {
            throw new IllegalArgumentException(
                    "Owner ID cannot be null"
            );
        }

        return knowledgeBaseRepository
                .findByOwnerIdAndStatus(
                        ownerId,
                        KnowledgeBase.Status.ACTIVE
                );
    }

    @Transactional(readOnly = true)
    public KnowledgeBase getBySlug(String slug) {

        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException(
                    "Slug cannot be null or blank"
            );
        }

        return knowledgeBaseRepository.findBySlug(slug)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Knowledge base not found with slug: "
                                        + slug
                        )
                );
    }

    public KnowledgeBase update(
            UUID id,
            String name,
            String description,
            String slug,
            UUID userId
    ) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        KnowledgeBase knowledgeBase =
                getActiveById(id);

        if (!knowledgeBase.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException(
                    "User is not the owner of this knowledge base"
            );
        }

        if (name != null && !name.isBlank()
                && !name.equals(knowledgeBase.getName())) {

            if (knowledgeBaseRepository
                    .existsByNameAndOwnerId(
                            name,
                            userId
                    )) {

                throw new IllegalArgumentException(
                        "Knowledge base with this name already exists"
                );
            }

            knowledgeBase.setName(name);
        }

        if (description != null) {
            knowledgeBase.setDescription(description);
        }

        if (slug != null && !slug.isBlank()
                && !slug.equals(knowledgeBase.getSlug())) {

            if (knowledgeBaseRepository.existsBySlug(slug)) {

                throw new IllegalArgumentException(
                        "Knowledge base slug already exists"
                );
            }

            knowledgeBase.setSlug(slug);
        }

        knowledgeBase.setUpdatedBy(userId);
        knowledgeBase.setUpdatedAt(LocalDateTime.now());

        knowledgeBase.setVersion(
                knowledgeBase.getVersion() + 1
        );

        return knowledgeBaseRepository.save(
                knowledgeBase
        );
    }

    public KnowledgeBase archive(
            UUID id,
            UUID userId
    ) {

        KnowledgeBase knowledgeBase =
                getActiveById(id);

        if (!knowledgeBase.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException(
                    "User is not the owner of this knowledge base"
            );
        }

        knowledgeBase.setStatus(
                KnowledgeBase.Status.ARCHIVED
        );

        knowledgeBase.setUpdatedBy(userId);
        knowledgeBase.setUpdatedAt(
                LocalDateTime.now()
        );

        knowledgeBase.setVersion(
                knowledgeBase.getVersion() + 1
        );

        return knowledgeBaseRepository.save(
                knowledgeBase
        );
    }

    public KnowledgeBase delete(
            UUID id,
            UUID userId
    ) {

        KnowledgeBase knowledgeBase =
                getById(id);

        if (!knowledgeBase.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException(
                    "User is not the owner of this knowledge base"
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

        knowledgeBase.setStatus(
                KnowledgeBase.Status.DELETED
        );

        knowledgeBase.setDeletedAt(now);
        knowledgeBase.setUpdatedBy(userId);
        knowledgeBase.setUpdatedAt(now);

        knowledgeBase.setVersion(
                knowledgeBase.getVersion() + 1
        );

        return knowledgeBaseRepository.save(
                knowledgeBase
        );


    }
    @Transactional
    public KnowledgeBase restore(UUID id) {

        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Knowledge base not found"));

        if (knowledgeBase.getStatus() != KnowledgeBase.Status.ARCHIVED) {
            throw new IllegalArgumentException(
                    "Only archived knowledge bases can be restored");
        }

        knowledgeBase.setStatus(KnowledgeBase.Status.ACTIVE);
        knowledgeBase.setDeletedAt(null);
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBase.setVersion(knowledgeBase.getVersion() + 1);

        return knowledgeBaseRepository.save(knowledgeBase);
    }
}