package com.omragul.knowledge.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class DocumentTagResponse {

    private UUID documentId;
    private UUID tagId;
    private UUID taggedBy;
    private LocalDateTime taggedAt;

    public DocumentTagResponse() {
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public void setTagId(UUID tagId) {
        this.tagId = tagId;
    }

    public UUID getTaggedBy() {
        return taggedBy;
    }

    public void setTaggedBy(UUID taggedBy) {
        this.taggedBy = taggedBy;
    }

    public LocalDateTime getTaggedAt() {
        return taggedAt;
    }

    public void setTaggedAt(LocalDateTime taggedAt) {
        this.taggedAt = taggedAt;
    }
}