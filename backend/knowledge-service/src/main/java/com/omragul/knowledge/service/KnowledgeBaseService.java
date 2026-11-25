package com.omragul.knowledge.service;

import com.omragul.knowledge.client.RagServiceClient;
import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.exception.DuplicateResourceException;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.exception.UnauthorizedException;
import com.omragul.knowledge.repository.KnowledgeBaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private RagServiceClient ragServiceClient;

    public KnowledgeBaseService(
            KnowledgeBaseRepository knowledgeBaseRepository
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @Autowired(required = false)
    public void setRagServiceClient(RagServiceClient ragServiceClient) {
        this.ragServiceClient = ragServiceClient;
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

        if (knowledgeBase.getSlug() == null || knowledgeBase.getSlug().isBlank()) {
            String generatedSlug = knowledgeBase.getName().trim().toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
            if (generatedSlug.isBlank()) {
                generatedSlug = "kb-" + UUID.randomUUID().toString().substring(0, 8);
            }
            if (knowledgeBaseRepository.existsBySlug(generatedSlug)) {
                generatedSlug = generatedSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
            }
            knowledgeBase.setSlug(generatedSlug);
        } else if (knowledgeBaseRepository.existsBySlug(
                knowledgeBase.getSlug())) {

            throw new DuplicateResourceException(
                    "Knowledge base slug already exists: " + knowledgeBase.getSlug()
            );
        }

        if (knowledgeBaseRepository.existsByNameAndOwnerId(
                knowledgeBase.getName(),
                userId)) {

            throw new DuplicateResourceException(
                    "Knowledge base with this name already exists: " + knowledgeBase.getName()
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
    public KnowledgeBase getById(UUID id, UUID userId) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Knowledge base ID cannot be null"
            );
        }

        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Knowledge base not found: " + id
                        )
                );

        if (knowledgeBase.getStatus() == KnowledgeBase.Status.DELETED) {
            throw new ResourceNotFoundException("Knowledge base not found: " + id);
        }

        if (knowledgeBase.getVisibility() == KnowledgeBase.Visibility.PRIVATE
                && !knowledgeBase.getOwnerId().equals(userId)) {
            throw new UnauthorizedException(
                    "User does not have access to private knowledge base: " + id
            );
        }

        return knowledgeBase;
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
                        new ResourceNotFoundException(
                                "Knowledge base not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public KnowledgeBase getActiveById(UUID id, UUID userId) {
        KnowledgeBase knowledgeBase = getById(id, userId);

        if (knowledgeBase.getStatus()
                != KnowledgeBase.Status.ACTIVE) {

            throw new IllegalArgumentException(
                    "Knowledge base is not active"
            );
        }

        return knowledgeBase;
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
    public KnowledgeBase getBySlug(String slug, UUID userId) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException(
                    "Slug cannot be null or blank"
            );
        }

        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findBySlug(slug)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Knowledge base not found with slug: "
                                        + slug
                        )
                );

        if (knowledgeBase.getStatus() == KnowledgeBase.Status.DELETED) {
            throw new ResourceNotFoundException("Knowledge base not found with slug: " + slug);
        }

        if (knowledgeBase.getVisibility() == KnowledgeBase.Visibility.PRIVATE
                && !knowledgeBase.getOwnerId().equals(userId)) {
            throw new UnauthorizedException(
                    "User does not have access to private knowledge base: " + slug
            );
        }

        return knowledgeBase;
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
                        new ResourceNotFoundException(
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
            throw new UnauthorizedException(
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

                throw new DuplicateResourceException(
                        "Knowledge base with this name already exists: " + name
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

                throw new DuplicateResourceException(
                        "Knowledge base slug already exists: " + slug
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
            throw new UnauthorizedException(
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
            throw new UnauthorizedException(
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

        KnowledgeBase saved = knowledgeBaseRepository.save(
                knowledgeBase
        );

        if (ragServiceClient != null) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            ragServiceClient.deleteKnowledgeBaseVectors(id);
                        } catch (Exception e) {
                            log.warn("Failed to synchronize vector deletion for KB {} after transaction commit: {}", id, e.getMessage());
                        }
                    }
                });
            } else {
                try {
                    ragServiceClient.deleteKnowledgeBaseVectors(id);
                } catch (Exception e) {
                    log.warn("Failed to synchronize vector deletion for KB {}: {}", id, e.getMessage());
                }
            }
        }

        return saved;


    }

    @Transactional
    public KnowledgeBase restore(UUID id) {
        throw new UnauthorizedException("Authenticated user ID is required");
    }

    @Transactional
    public KnowledgeBase restore(UUID id, UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Knowledge base not found: " + id));

        if (!knowledgeBase.getOwnerId().equals(userId)) {
            throw new UnauthorizedException("User is not the owner of this knowledge base");
        }

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