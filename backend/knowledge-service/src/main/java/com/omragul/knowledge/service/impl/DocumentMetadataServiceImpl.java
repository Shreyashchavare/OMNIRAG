package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.DocumentMetadata;
import com.omragul.knowledge.repository.DocumentMetadataRepository;
import com.omragul.knowledge.service.DocumentMetadataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DocumentMetadataServiceImpl
        implements DocumentMetadataService {

    private final DocumentMetadataRepository metadataRepository;

    public DocumentMetadataServiceImpl(
            DocumentMetadataRepository metadataRepository
    ) {
        this.metadataRepository = metadataRepository;
    }

    @Override
    public DocumentMetadata create(
            DocumentMetadata metadata
    ) {
        metadata.setCreatedAt(LocalDateTime.now());

        if (metadata.getValueType() == null) {
            metadata.setValueType(
                    DocumentMetadata.ValueType.STRING
            );
        }

        return metadataRepository.save(metadata);
    }

    @Override
    public DocumentMetadata update(
            DocumentMetadata metadata
    ) {
        return metadataRepository.save(metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentMetadata getById(
            UUID metadataId
    ) {
        return metadataRepository.findById(metadataId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document metadata not found: "
                                        + metadataId
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentMetadata> getByDocumentId(
            UUID documentId
    ) {
        return metadataRepository.findByDocumentId(
                documentId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentMetadata getByDocumentIdAndKey(
            UUID documentId,
            String metadataKey
    ) {
        return metadataRepository
                .findByDocumentIdAndMetadataKey(
                        documentId,
                        metadataKey
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Metadata not found for key: "
                                        + metadataKey
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDocumentIdAndKey(
            UUID documentId,
            String metadataKey
    ) {
        return metadataRepository
                .existsByDocumentIdAndMetadataKey(
                        documentId,
                        metadataKey
                );
    }

    @Override
    public DocumentMetadata upsert(
            DocumentMetadata metadata
    ) {
        return metadataRepository
                .findByDocumentIdAndMetadataKey(
                        metadata.getDocumentId(),
                        metadata.getMetadataKey()
                )
                .map(existing -> {

                    existing.setMetadataValue(
                            metadata.getMetadataValue()
                    );

                    existing.setValueType(
                            metadata.getValueType()
                    );

                    return metadataRepository.save(existing);
                })
                .orElseGet(() ->
                        metadataRepository.save(metadata)
                );
    }

    @Override
    public void delete(
            UUID metadataId
    ) {
        if (!metadataRepository.existsById(metadataId)) {
            throw new RuntimeException(
                    "Document metadata not found: "
                            + metadataId
            );
        }

        metadataRepository.deleteById(metadataId);
    }

    @Override
    public void deleteByDocumentIdAndKey(
            UUID documentId,
            String metadataKey
    ) {
        if (!metadataRepository
                .existsByDocumentIdAndMetadataKey(
                        documentId,
                        metadataKey
                )) {

            throw new RuntimeException(
                    "Metadata not found for key: "
                            + metadataKey
            );
        }

        metadataRepository
                .deleteByDocumentIdAndMetadataKey(
                        documentId,
                        metadataKey
                );
    }
}