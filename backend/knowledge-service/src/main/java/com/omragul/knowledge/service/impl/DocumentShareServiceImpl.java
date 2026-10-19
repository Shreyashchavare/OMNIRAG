package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.DocumentShare;
import com.omragul.knowledge.repository.DocumentShareRepository;
import com.omragul.knowledge.service.DocumentShareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DocumentShareServiceImpl
        implements DocumentShareService {

    private final DocumentShareRepository shareRepository;

    public DocumentShareServiceImpl(
            DocumentShareRepository shareRepository
    ) {
        this.shareRepository = shareRepository;
    }

    @Override
    public DocumentShare createShare(
            UUID documentId,
            UUID sharedBy,
            UUID sharedWith,
            DocumentShare.AccessLevel accessLevel,
            LocalDateTime expiresAt
    ) {

        if (sharedBy.equals(sharedWith)) {
            throw new IllegalArgumentException(
                    "Document cannot be shared with the owner"
            );
        }

        if (expiresAt != null
                && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Expiration time must be in the future"
            );
        }

        if (shareRepository.existsByDocumentIdAndSharedWith(
                documentId,
                sharedWith
        )) {
            throw new IllegalStateException(
                    "Document is already shared with this user"
            );
        }

        DocumentShare share = new DocumentShare();

        share.setDocumentId(documentId);
        share.setSharedBy(sharedBy);
        share.setSharedWith(sharedWith);
        share.setAccessLevel(accessLevel);
        share.setExpiresAt(expiresAt);
        share.setCreatedAt(LocalDateTime.now());

        return shareRepository.save(share);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentShare getById(
            UUID shareId
    ) {
        return shareRepository.findById(shareId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document share not found: "
                                        + shareId
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentShare> getByDocumentId(
            UUID documentId
    ) {
        return shareRepository.findByDocumentId(
                documentId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentShare> getBySharedWith(
            UUID sharedWith
    ) {
        return shareRepository.findBySharedWith(
                sharedWith
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentShare> getBySharedBy(
            UUID sharedBy
    ) {
        return shareRepository.findBySharedBy(
                sharedBy
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentShare> getByDocumentAndUser(
            UUID documentId,
            UUID sharedWith
    ) {
        return shareRepository
                .findByDocumentIdAndSharedWith(
                        documentId,
                        sharedWith
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentShare> getActiveShares(
            UUID documentId
    ) {
        return shareRepository.findActiveShares(
                documentId,
                LocalDateTime.now()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentShare> getActiveSharesForUser(
            UUID userId
    ) {
        return shareRepository.findActiveSharesForUser(
                userId,
                LocalDateTime.now()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSharedWith(
            UUID documentId,
            UUID userId
    ) {
        return shareRepository
                .findActiveShares(documentId, LocalDateTime.now())
                .stream()
                .anyMatch(share ->
                        share.getSharedWith().equals(userId)
                );
    }

    @Override
    public void revokeShare(
            UUID shareId
    ) {
        if (!shareRepository.existsById(shareId)) {
            throw new RuntimeException(
                    "Document share not found: "
                            + shareId
            );
        }

        shareRepository.deleteById(shareId);
    }

    @Override
    public void revokeShareFromUser(
            UUID documentId,
            UUID sharedWith
    ) {
        shareRepository.deleteByDocumentIdAndSharedWith(
                documentId,
                sharedWith
        );
    }

    @Override
    public void revokeAllShares(
            UUID documentId
    ) {
        shareRepository.deleteByDocumentId(
                documentId
        );
    }
}