package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.DocumentPermission;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public class DocumentPermissionRequest {

    @NotNull
    private UUID documentId;

    @NotNull
    private DocumentPermission.PrincipalType principalType;

    @NotNull
    private UUID principalId;

    @NotNull
    private DocumentPermission.Permission permission;

    private LocalDateTime expiresAt;

    public DocumentPermissionRequest() {
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public DocumentPermission.PrincipalType getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(DocumentPermission.PrincipalType principalType) {
        this.principalType = principalType;
    }

    public UUID getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(UUID principalId) {
        this.principalId = principalId;
    }

    public DocumentPermission.Permission getPermission() {
        return permission;
    }

    public void setPermission(DocumentPermission.Permission permission) {
        this.permission = permission;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}