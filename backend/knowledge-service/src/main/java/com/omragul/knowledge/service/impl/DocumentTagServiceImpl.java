package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.DocumentTag;
import com.omragul.knowledge.repository.DocumentTagRepository;
import com.omragul.knowledge.service.DocumentTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DocumentTagServiceImpl
        implements DocumentTagService {

    private final DocumentTagRepository documentTagRepository;

    public DocumentTagServiceImpl(
            DocumentTagRepository documentTagRepository
    ) {
        this.documentTagRepository = documentTagRepository;
    }

    @Override
    public DocumentTag addTag(
            UUID documentId,
            UUID tagId,
            UUID taggedBy
    ) {

        if (documentTagRepository
                .existsByDocumentIdAndTagId(
                        documentId,
                        tagId
                )) {

            throw new RuntimeException(
                    "Tag is already assigned to document"
            );
        }

        DocumentTag documentTag = new DocumentTag();

        documentTag.setDocumentId(documentId);
        documentTag.setTagId(tagId);
        documentTag.setTaggedBy(taggedBy);
        documentTag.setTaggedAt(LocalDateTime.now());

        return documentTagRepository.save(documentTag);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTag> getByDocumentId(
            UUID documentId
    ) {
        return documentTagRepository.findByDocumentId(
                documentId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTag> getByTagId(
            UUID tagId
    ) {
        return documentTagRepository.findByTagId(
                tagId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(
            UUID documentId,
            UUID tagId
    ) {
        return documentTagRepository
                .existsByDocumentIdAndTagId(
                        documentId,
                        tagId
                );
    }

    @Override
    public void removeTag(
            UUID documentId,
            UUID tagId
    ) {

        if (!documentTagRepository
                .existsByDocumentIdAndTagId(
                        documentId,
                        tagId
                )) {

            throw new RuntimeException(
                    "Tag is not assigned to document"
            );
        }

        documentTagRepository
                .deleteByDocumentIdAndTagId(
                        documentId,
                        tagId
                );
    }

    @Override
    public void removeAllTagsFromDocument(
            UUID documentId
    ) {
        documentTagRepository.deleteByDocumentId(
                documentId
        );
    }

    @Override
    public void removeAllDocumentsFromTag(
            UUID tagId
    ) {
        documentTagRepository.deleteByTagId(
                tagId
        );
    }
}