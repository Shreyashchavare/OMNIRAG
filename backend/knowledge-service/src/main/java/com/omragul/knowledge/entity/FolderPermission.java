package com.omragul.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "folder_permissions",
        indexes = {
                @Index(name = "idx_folder_permission_folder", columnList = "folder_id"),
                @Index(name = "idx_folder_permission_principal", columnList = "principal_id")
        }
)
public class FolderPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "folder_id", nullable = false)
    private UUID folderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", length = 20, nullable = false)
    private PrincipalType principalType;

    @Column(name = "principal_id", nullable = false)
    private UUID principalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 20, nullable = false)
    private Permission permission;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public enum PrincipalType {
        USER,
        ROLE
    }

    public enum Permission {
        READ,
        WRITE,
        DELETE,
        UPLOAD,
        OWNER
    }

    public FolderPermission() {
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

    public PrincipalType getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(PrincipalType principalType) {
        this.principalType = principalType;
    }

    public UUID getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(UUID principalId) {
        this.principalId = principalId;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
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