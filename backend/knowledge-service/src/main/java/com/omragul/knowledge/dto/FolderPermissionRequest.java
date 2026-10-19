package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.FolderPermission;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public class FolderPermissionRequest {

    @NotNull
    private UUID folderId;

    @NotNull
    private FolderPermission.PrincipalType principalType;

    @NotNull
    private UUID principalId;

    @NotNull
    private FolderPermission.Permission permission;

    private LocalDateTime expiresAt;

    public FolderPermissionRequest() {
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

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}