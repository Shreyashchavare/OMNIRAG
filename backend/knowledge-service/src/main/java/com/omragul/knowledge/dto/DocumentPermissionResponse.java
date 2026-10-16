package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.DocumentPermission;

import java.time.LocalDateTime;
import java.util.UUID;

public class DocumentPermissionResponse {

    private UUID id;
    private UUID documentId;
    private DocumentPermission.PrincipalType principalType;
    private UUID principalId;
    private DocumentPermission.Permission permission;
    private UUID grantedBy;
    private LocalDateTime grantedAt;
    private LocalDateTime expiresAt;

    public DocumentPermissionResponse() {
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

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(UUID grantedBy) {
        this.grantedBy = grantedBy;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}