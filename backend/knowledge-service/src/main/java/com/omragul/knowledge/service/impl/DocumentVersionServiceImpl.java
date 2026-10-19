package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.DocumentVersion;
import com.omragul.knowledge.repository.DocumentVersionRepository;
import com.omragul.knowledge.service.DocumentVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DocumentVersionServiceImpl
        implements DocumentVersionService {

    private final DocumentVersionRepository documentVersionRepository;

    public DocumentVersionServiceImpl(
            DocumentVersionRepository documentVersionRepository
    ) {
        this.documentVersionRepository = documentVersionRepository;
    }

    @Override
    public DocumentVersion create(
            DocumentVersion documentVersion
    ) {
        documentVersion.setCreatedAt(LocalDateTime.now());

        return documentVersionRepository.save(documentVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentVersion getById(
            UUID versionId
    ) {
        return documentVersionRepository.findById(versionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document version not found: "
                                        + versionId
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentVersion getVersion(
            UUID documentId,
            Integer versionNumber
    ) {
        return documentVersionRepository
                .findByDocumentIdAndVersionNumber(
                        documentId,
                        versionNumber
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document version not found"
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentVersion getLatestVersion(
            UUID documentId
    ) {
        return documentVersionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(
                        documentId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "No versions found for document: "
                                        + documentId
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentVersion> getVersions(
            UUID documentId
    ) {
        return documentVersionRepository
                .findByDocumentIdOrderByVersionNumberDesc(
                        documentId
                );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentVersion getByChecksum(
            String checksum
    ) {
        return documentVersionRepository
                .findByChecksum(checksum)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document version not found for checksum"
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByChecksum(
            String checksum
    ) {
        return documentVersionRepository
                .existsByChecksum(checksum);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsVersion(
            UUID documentId,
            Integer versionNumber
    ) {
        return documentVersionRepository
                .existsByDocumentIdAndVersionNumber(
                        documentId,
                        versionNumber
                );
    }

    @Override
    @Transactional(readOnly = true)
    public int getNextVersionNumber(
            UUID documentId
    ) {
        return documentVersionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(
                        documentId
                )
                .map(version ->
                        version.getVersionNumber() + 1
                )
                .orElse(1);
    }

    @Override
    @Transactional(readOnly = true)
    public long countVersions(
            UUID documentId
    ) {
        return documentVersionRepository
                .countByDocumentId(documentId);
    }
}