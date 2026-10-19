package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.repository.DocumentRepository;
import com.omragul.knowledge.service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentServiceImpl(
            DocumentRepository documentRepository
    ) {
        this.documentRepository = documentRepository;
    }

    @Override
    public Document create(Document document) {

        LocalDateTime now = LocalDateTime.now();

        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        document.setCurrentVersion(1);
        document.setProcessingStatus(
                Document.ProcessingStatus.PENDING
        );
        document.setStatus(
                Document.Status.ACTIVE
        );
        document.setVersion(1);

        return documentRepository.save(document);
    }
    @Override
    @Transactional(readOnly = true)
    public Document getById(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document not found: " + documentId
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Document getByKnowledgeBaseAndId(
            UUID knowledgeBaseId,
            UUID documentId
    ) {
        return documentRepository
                .findByKnowledgeBaseIdAndId(
                        knowledgeBaseId,
                        documentId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document not found: " + documentId
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByKnowledgeBase(
            UUID knowledgeBaseId
    ) {
        return documentRepository
                .findByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> getByKnowledgeBase(
            UUID knowledgeBaseId,
            Pageable pageable
    ) {
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
            Document.Status status
    ) {
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
            Pageable pageable
    ) {
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
            Document.ProcessingStatus processingStatus
    ) {
        return documentRepository
                .findByKnowledgeBaseIdAndProcessingStatus(
                        knowledgeBaseId,
                        processingStatus
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByFolder(
            UUID folderId
    ) {
        return documentRepository.findByFolderId(folderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> getByFolder(
            UUID folderId,
            Pageable pageable
    ) {
        return documentRepository.findByFolderId(
                folderId,
                pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByCategory(
            UUID categoryId
    ) {
        return documentRepository.findByCategoryId(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> getByCategory(
            UUID categoryId,
            Pageable pageable
    ) {
        return documentRepository.findByCategoryId(
                categoryId,
                pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getByOwner(
            UUID ownerId
    ) {
        return documentRepository.findByOwnerId(ownerId);
    }

    @Override
    public Document update(
            UUID documentId,
            Document document
    ) {
        Document existing = getById(documentId);

        existing.setFolderId(document.getFolderId());
        existing.setCategoryId(document.getCategoryId());

        existing.setName(document.getName());
        existing.setOriginalFilename(
                document.getOriginalFilename()
        );
        existing.setStoredFilename(
                document.getStoredFilename()
        );
        existing.setStoragePath(
                document.getStoragePath()
        );
        existing.setFileExtension(
                document.getFileExtension()
        );
        existing.setMimeType(
                document.getMimeType()
        );
        existing.setFileSizeBytes(
                document.getFileSizeBytes()
        );
        existing.setChecksum(
                document.getChecksum()
        );

        existing.setUpdatedAt(LocalDateTime.now());
        existing.setVersion(existing.getVersion() + 1);

        return documentRepository.save(existing);
    }

    @Override
    public Document updateProcessingStatus(
            UUID documentId,
            Document.ProcessingStatus processingStatus
    ) {
        Document document = getById(documentId);

        document.setProcessingStatus(processingStatus);
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion(document.getVersion() + 1);

        return documentRepository.save(document);
    }

    @Override
    public Document archive(UUID documentId) {
        Document document = getById(documentId);

        document.setStatus(Document.Status.ARCHIVED);
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion(document.getVersion() + 1);

        return documentRepository.save(document);
    }

    @Override
    public Document restore(UUID documentId) {
        Document document = getById(documentId);

        document.setStatus(Document.Status.ACTIVE);
        document.setDeletedAt(null);
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion(document.getVersion() + 1);

        return documentRepository.save(document);
    }

    @Override
    public Document softDelete(UUID documentId) {
        Document document = getById(documentId);

        document.setStatus(Document.Status.DELETED);
        document.setDeletedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion(document.getVersion() + 1);

        return documentRepository.save(document);
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
}