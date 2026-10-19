package com.omragul.knowledge.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class DocumentTagRequest {

    @NotNull
    private UUID documentId;

    @NotNull
    private UUID tagId;

    public DocumentTagRequest() {
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
}