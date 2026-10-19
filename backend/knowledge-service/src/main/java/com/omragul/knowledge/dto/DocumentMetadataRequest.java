package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.DocumentMetadata;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class DocumentMetadataRequest {

    @NotNull
    private UUID documentId;

    @NotBlank
    private String metadataKey;

    private String metadataValue;

    @NotNull
    private DocumentMetadata.ValueType valueType;

    public DocumentMetadataRequest() {
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

    public void setValueType(
            DocumentMetadata.ValueType valueType
    ) {
        this.valueType = valueType;
    }
}