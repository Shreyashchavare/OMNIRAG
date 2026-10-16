package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.DocumentShare;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public class DocumentShareRequest {

    @NotNull
    private UUID documentId;

    @NotNull
    private UUID sharedWith;

    @NotNull
    private DocumentShare.AccessLevel accessLevel;

    private LocalDateTime expiresAt;

    public DocumentShareRequest() {
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
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

    public void setAccessLevel(DocumentShare.AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}