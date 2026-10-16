package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.FolderPermission;

import java.time.LocalDateTime;
import java.util.UUID;

public class FolderPermissionResponse {

    private UUID id;
    private UUID folderId;
    private FolderPermission.PrincipalType principalType;
    private UUID principalId;
    private FolderPermission.Permission permission;
    private UUID grantedBy;
    private LocalDateTime grantedAt;
    private LocalDateTime expiresAt;

    public FolderPermissionResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public FolderPermission.PrincipalType getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(FolderPermission.PrincipalType principalType) {
        this.principalType = principalType;
    }

    public UUID getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(UUID principalId) {
        this.principalId = principalId;
    }

    public FolderPermission.Permission getPermission() {
        return permission;
    }

    public void setPermission(FolderPermission.Permission permission) {
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