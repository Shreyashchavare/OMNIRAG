package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.client.RagServiceClient;
import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.entity.DocumentPermission;
import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.exception.UnauthorizedException;
import com.omragul.knowledge.repository.CategoryRepository;
import com.omragul.knowledge.repository.DocumentRepository;
import com.omragul.knowledge.repository.FolderRepository;
import com.omragul.knowledge.repository.KnowledgeBaseRepository;
import com.omragul.knowledge.service.DocumentPermissionService;
import com.omragul.knowledge.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentPermissionService documentPermissionService;
    private final FolderRepository folderRepository;
    private final CategoryRepository categoryRepository;
    private RagServiceClient ragServiceClient;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentPermissionService documentPermissionService,
            FolderRepository folderRepository,
            CategoryRepository categoryRepository
    ) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentPermissionService = documentPermissionService;
        this.folderRepository = folderRepository;
        this.categoryRepository = categoryRepository;
    }

    @Autowired(required = false)
    public void setRagServiceClient(RagServiceClient ragServiceClient) {
        this.ragServiceClient = ragServiceClient;
    }

    private KnowledgeBase checkKnowledgeBaseWriteAccess(UUID kbId, UUID userId) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge base not found: " + kbId));

        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        if (kb.getOwnerId().equals(userId)) {
            return kb;
        }

        if (kb.getVisibility() == KnowledgeBase.Visibility.PRIVATE) {
            throw new UnauthorizedException("User is not authorized to create documents in this private knowledge base");
        }

        return kb;
    }

    private void checkKnowledgeBaseReadAccess(UUID kbId, UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge base not found: " + kbId));

        if (kb.getOwnerId().equals(userId)) {
            return;
        }

        if (kb.getVisibility() != KnowledgeBase.Visibility.PRIVATE) {
            return;
        }

        throw new UnauthorizedException("User does not have access to private knowledge base: " + kbId);
    }

    private void checkDocumentReadAccess(Document document, UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        if (document.getOwnerId() != null && document.getOwnerId().equals(userId)) {
            return;
        }

        if (documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.READ)
                || documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.WRITE)
                || documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.OWNER)) {
            return;
        }

        KnowledgeBase kb = knowledgeBaseRepository.findById(document.getKnowledgeBaseId()).orElse(null);
        if (kb != null) {
            if (kb.getOwnerId().equals(userId)) {
                return;
            }
            if (kb.getVisibility() != KnowledgeBase.Visibility.PRIVATE) {
                return;
            }
        }

        throw new UnauthorizedException("User does not have permission to view document: " + document.getId());
    }

    private void checkDocumentWriteAccess(Document document, UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        if (document.getOwnerId() != null && document.getOwnerId().equals(userId)) {
            return;
        }

        if (documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.WRITE)
                || documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.OWNER)) {
            return;
        }

        KnowledgeBase kb = knowledgeBaseRepository.findById(document.getKnowledgeBaseId()).orElse(null);
        if (kb != null && kb.getOwnerId().equals(userId)) {
            return;
        }

        throw new UnauthorizedException("User does not have permission to update document: " + document.getId());
    }

    private void checkDocumentAdminAccess(Document document, UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        if (document.getOwnerId() != null && document.getOwnerId().equals(userId)) {
            return;
        }

        if (documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.OWNER)) {
            return;
        }

        KnowledgeBase kb = knowledgeBaseRepository.findById(document.getKnowledgeBaseId()).orElse(null);
        if (kb != null && kb.getOwnerId().equals(userId)) {
            return;
        }

        throw new UnauthorizedException("User does not have permission to manage document: " + document.getId());
    }

    private void checkDocumentDeleteAccess(Document document, UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }

        if (document.getOwnerId() != null && document.getOwnerId().equals(userId)) {
            return;
        }

        if (documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.DELETE)
                || documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.OWNER)) {
            return;
        }

        KnowledgeBase kb = knowledgeBaseRepository.findById(document.getKnowledgeBaseId()).orElse(null);
        if (kb != null && kb.getOwnerId().equals(userId)) {
            return;
        }

        throw new UnauthorizedException("User does not have permission to delete document: " + document.getId());
    }

    @Override
    public Document create(Document document, UUID userId) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }
        if (document.getKnowledgeBaseId() == null) {
            throw new IllegalArgumentException("Knowledge base ID is required");
        }

        checkKnowledgeBaseWriteAccess(document.getKnowledgeBaseId(), userId);

        document.setOwnerId(userId);

        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        document.setCurrentVersion(1);
        document.setProcessingStatus(Document.ProcessingStatus.PENDING);
        document.setStatus(Document.Status.ACTIVE);
        document.setVersion(1);

        return documentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Document getById(UUID documentId, UUID userId, boolean isInternalService) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (!isInternalService) {
            checkDocumentReadAccess(document, userId);
        }

        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public Document getById(UUID documentId, UUID userId) {
        return getById(documentId, userId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Document getByKnowledgeBaseAndId(
            UUID knowledgeBaseId,
            UUID documentId,
            UUID userId
    ) {
        Document document = documentRepository
                .findByKnowledgeBaseIdAndId(
                        knowledgeBaseId,
                        documentId
                )
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        checkDocumentReadAccess(document, userId);

        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByKnowledgeBase(
            UUID knowledgeBaseId,
            UUID userId
    ) {
        checkKnowledgeBaseReadAccess(knowledgeBaseId, userId);
        return documentRepository
                .findByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> getByKnowledgeBase(
            UUID knowledgeBaseId,
            UUID userId,
            Pageable pageable
    ) {
        checkKnowledgeBaseReadAccess(knowledgeBaseId, userId);
        return documentRepository
                .findByKnowledgeBaseId(
                        knowledgeBaseId,
                        pageable
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByKnowledgeBaseAndStatus(
            UUID knowledgeBaseId,
            Document.Status status,
            UUID userId
    ) {
        checkKnowledgeBaseReadAccess(knowledgeBaseId, userId);
        return documentRepository
                .findByKnowledgeBaseIdAndStatus(
                        knowledgeBaseId,
                        status
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> getByKnowledgeBaseAndStatus(
            UUID knowledgeBaseId,
            Document.Status status,
            UUID userId,
            Pageable pageable
    ) {
        checkKnowledgeBaseReadAccess(knowledgeBaseId, userId);
        return documentRepository
                .findByKnowledgeBaseIdAndStatus(
                        knowledgeBaseId,
                        status,
                        pageable
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByProcessingStatus(
            UUID knowledgeBaseId,
            Document.ProcessingStatus processingStatus,
            UUID userId
    ) {
        checkKnowledgeBaseReadAccess(knowledgeBaseId, userId);
        return documentRepository
                .findByKnowledgeBaseIdAndProcessingStatus(
                        knowledgeBaseId,
                        processingStatus
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByFolder(
            UUID folderId,
            UUID userId
    ) {
        var folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
        checkKnowledgeBaseReadAccess(folder.getKnowledgeBaseId(), userId);
        return documentRepository.findByFolderId(folderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> getByFolder(
            UUID folderId,
            UUID userId,
            Pageable pageable
    ) {
        var folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
        checkKnowledgeBaseReadAccess(folder.getKnowledgeBaseId(), userId);
        return documentRepository.findByFolderId(
                folderId,
                pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByCategory(
            UUID categoryId,
            UUID userId
    ) {
        var category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        checkKnowledgeBaseReadAccess(category.getKnowledgeBaseId(), userId);
        return documentRepository.findByCategoryId(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> getByCategory(
            UUID categoryId,
            UUID userId,
            Pageable pageable
    ) {
        var category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        checkKnowledgeBaseReadAccess(category.getKnowledgeBaseId(), userId);
        return documentRepository.findByCategoryId(
                categoryId,
                pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByOwner(
            UUID ownerId,
            UUID userId
    ) {
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user ID is required");
        }
        if (!ownerId.equals(userId)) {
            throw new UnauthorizedException("User is not authorized to list documents of another owner");
        }
        return documentRepository.findByOwnerId(ownerId);
    }

    @Override
    public Document update(
            UUID documentId,
            Document document,
            UUID userId
    ) {
        Document existing = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        checkDocumentWriteAccess(existing, userId);

        existing.setFolderId(document.getFolderId());
        existing.setCategoryId(document.getCategoryId());

        if (document.getName() != null && !document.getName().isBlank()) {
            existing.setName(document.getName());
        }
        if (document.getOriginalFilename() != null) {
            existing.setOriginalFilename(document.getOriginalFilename());
        }
        if (document.getStoredFilename() != null) {
            existing.setStoredFilename(document.getStoredFilename());
        }
        if (document.getStoragePath() != null) {
            existing.setStoragePath(document.getStoragePath());
        }
        if (document.getFileExtension() != null) {
            existing.setFileExtension(document.getFileExtension());
        }
        if (document.getMimeType() != null) {
            existing.setMimeType(document.getMimeType());
        }
        if (document.getFileSizeBytes() != null) {
            existing.setFileSizeBytes(document.getFileSizeBytes());
        }
        if (document.getChecksum() != null) {
            existing.setChecksum(document.getChecksum());
        }

        existing.setUpdatedAt(LocalDateTime.now());
        existing.setVersion(existing.getVersion() + 1);

        return documentRepository.save(existing);
    }

    @Override
    public Document updateProcessingStatus(
            UUID documentId,
            Document.ProcessingStatus processingStatus
    ) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        document.setProcessingStatus(processingStatus);
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion(document.getVersion() + 1);

        return documentRepository.save(document);
    }

    @Override
    public Document archive(UUID documentId, UUID userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        checkDocumentAdminAccess(document, userId);

        document.setStatus(Document.Status.ARCHIVED);
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion(document.getVersion() + 1);

        return documentRepository.save(document);
    }

    @Override
    public Document restore(UUID documentId, UUID userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        checkDocumentAdminAccess(document, userId);

        document.setStatus(Document.Status.ACTIVE);
        document.setDeletedAt(null);
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion(document.getVersion() + 1);

        return documentRepository.save(document);
    }

    @Override
    public Document softDelete(UUID documentId, UUID userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        checkDocumentDeleteAccess(document, userId);

        document.setStatus(Document.Status.DELETED);
        document.setDeletedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion(document.getVersion() + 1);

        Document saved = documentRepository.save(document);

        if (ragServiceClient != null && document.getKnowledgeBaseId() != null) {
            UUID kbId = document.getKnowledgeBaseId();
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            ragServiceClient.deleteDocumentVectors(documentId, kbId);
                        } catch (Exception e) {
                            log.warn("Failed to synchronize vector deletion for document {} after transaction commit: {}", documentId, e.getMessage());
                        }
                    }
                });
            } else {
                try {
                    ragServiceClient.deleteDocumentVectors(documentId, kbId);
                } catch (Exception e) {
                    log.warn("Failed to synchronize vector deletion for document {}: {}", documentId, e.getMessage());
                }
            }
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getActiveDocumentIds(UUID knowledgeBaseId, Collection<UUID> documentIds) {
        if (knowledgeBaseId == null || documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return documentRepository.findByKnowledgeBaseIdAndIdInAndStatus(
                knowledgeBaseId,
                documentIds,
                Document.Status.ACTIVE
        ).stream().map(Document::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByChecksum(String checksum) {
        return documentRepository.existsByChecksum(checksum);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByKnowledgeBaseAndName(
            UUID knowledgeBaseId,
            String name
    ) {
        return documentRepository
                .existsByKnowledgeBaseIdAndName(
                        knowledgeBaseId,
                        name
                );
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveDocuments(
            UUID knowledgeBaseId
    ) {
        return documentRepository.countByKnowledgeBaseIdAndStatus(
                knowledgeBaseId,
                Document.Status.ACTIVE
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveDocumentsInFolder(
            UUID folderId
    ) {
        return documentRepository.countByFolderIdAndStatus(
                folderId,
                Document.Status.ACTIVE
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveDocumentsInCategory(
            UUID categoryId
    ) {
        return documentRepository.countByCategoryIdAndStatus(
                categoryId,
                Document.Status.ACTIVE
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID documentId) {
        if (documentId == null) {
            return false;
        }
        return documentRepository.existsById(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDocumentWriteAccess(UUID documentId, UUID userId) {
        if (userId == null || documentId == null) {
            return false;
        }
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return false;
        }
        if (document.getOwnerId() != null && document.getOwnerId().equals(userId)) {
            return true;
        }
        if (documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.WRITE)
                || documentPermissionService.hasPermission(document.getId(), userId, DocumentPermission.Permission.OWNER)) {
            return true;
        }
        if (document.getKnowledgeBaseId() != null) {
            KnowledgeBase kb = knowledgeBaseRepository.findById(document.getKnowledgeBaseId()).orElse(null);
            if (kb != null && kb.getOwnerId() != null && kb.getOwnerId().equals(userId)) {
                return true;
            }
        }
        return false;
    }
}
