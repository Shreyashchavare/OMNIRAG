package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.FolderPermission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FolderPermissionService {

    FolderPermission grantPermission(
            UUID folderId,
            FolderPermission.PrincipalType principalType,
            UUID principalId,
            FolderPermission.Permission permission,
            UUID grantedBy,
            LocalDateTime expiresAt
    );

    FolderPermission getById(
            UUID permissionId
    );

    List<FolderPermission> getByFolderId(
            UUID folderId
    );

    List<FolderPermission> getByPrincipalId(
            UUID principalId
    );

    List<FolderPermission> getByFolderAndPrincipal(
            UUID folderId,
            UUID principalId
    );

    List<FolderPermission> getByPrincipalType(
            UUID folderId,
            FolderPermission.PrincipalType principalType
    );

    boolean hasPermission(
            UUID folderId,
            UUID principalId,
            FolderPermission.Permission permission
    );

    List<FolderPermission> getActivePermissions(
            UUID folderId
    );

    void revokePermission(
            UUID permissionId
    );

    void revokePermissionsForPrincipal(
            UUID folderId,
            UUID principalId
    );

    void revokeAllPermissions(
            UUID folderId
    );
}