package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.DocumentMetadata;

import java.time.LocalDateTime;
import java.util.UUID;

public class DocumentMetadataResponse {

    private UUID id;
    private UUID documentId;
    private String metadataKey;
    private String metadataValue;
    private DocumentMetadata.ValueType valueType;
    private LocalDateTime createdAt;
    private UUID createdBy;

    public DocumentMetadataResponse() {
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

    public String getMetadataKey() {
        return metadataKey;
    }

    public void setMetadataKey(String metadataKey) {
        this.metadataKey = metadataKey;
    }

    public String getMetadataValue() {
        return metadataValue;
    }

    public void setMetadataValue(String metadataValue) {
        this.metadataValue = metadataValue;
    }

    public DocumentMetadata.ValueType getValueType() {
        return valueType;
    }

    public void setValueType(DocumentMetadata.ValueType valueType) {
        this.valueType = valueType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}