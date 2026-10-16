package com.omragul.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class FolderCreateRequest {

    @NotNull
    private UUID knowledgeBaseId;

    private UUID parentFolderId;

    @NotBlank
    @Size(max = 255)
    private String name;

    public FolderCreateRequest() {
    }

    public UUID getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(UUID knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public UUID getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(UUID parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}