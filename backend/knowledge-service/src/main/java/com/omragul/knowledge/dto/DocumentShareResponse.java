package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.DocumentShare;

import java.time.LocalDateTime;
import java.util.UUID;

public class DocumentShareResponse {

    private UUID id;
    private UUID documentId;
    private UUID sharedBy;
    private UUID sharedWith;
    private DocumentShare.AccessLevel accessLevel;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public DocumentShareResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getSharedBy() {
        return sharedBy;
    }

    public void setSharedBy(UUID sharedBy) {
        this.sharedBy = sharedBy;
    }

    public UUID getSharedWith() {
        return sharedWith;
    }

    public void setSharedWith(UUID sharedWith) {
        this.sharedWith = sharedWith;
    }

    public DocumentShare.AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(
            DocumentShare.AccessLevel accessLevel
    ) {
        this.accessLevel = accessLevel;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}