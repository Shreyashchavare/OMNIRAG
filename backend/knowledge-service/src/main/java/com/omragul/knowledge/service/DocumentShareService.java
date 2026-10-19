package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.DocumentShare;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DocumentShareService {

    DocumentShare createShare(
            UUID documentId,
            UUID sharedBy,
            UUID sharedWith,
            DocumentShare.AccessLevel accessLevel,
            LocalDateTime expiresAt
    );

    DocumentShare getById(
            UUID shareId
    );

    List<DocumentShare> getByDocumentId(
            UUID documentId
    );

    List<DocumentShare> getBySharedWith(
            UUID sharedWith
    );

    List<DocumentShare> getBySharedBy(
            UUID sharedBy
    );

    List<DocumentShare> getByDocumentAndUser(
            UUID documentId,
            UUID sharedWith
    );

    List<DocumentShare> getActiveShares(
            UUID documentId
    );

    List<DocumentShare> getActiveSharesForUser(
            UUID userId
    );

    boolean isSharedWith(
            UUID documentId,
            UUID userId
    );

    void revokeShare(
            UUID shareId
    );

    void revokeShareFromUser(
            UUID documentId,
            UUID sharedWith
    );

    void revokeAllShares(
            UUID documentId
    );
}