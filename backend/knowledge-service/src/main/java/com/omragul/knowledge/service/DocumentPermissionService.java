package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.DocumentPermission;

import java.util.List;
import java.util.UUID;

public interface DocumentPermissionService {

    DocumentPermission grantPermission(
            UUID documentId,
            DocumentPermission.PrincipalType principalType,
            UUID principalId,
            DocumentPermission.Permission permission,
            UUID grantedBy,
            java.time.LocalDateTime expiresAt
    );

    DocumentPermission getById(
            UUID permissionId
    );

    List<DocumentPermission> getByDocumentId(
            UUID documentId
    );

    List<DocumentPermission> getByPrincipalId(
            UUID principalId
    );

    List<DocumentPermission> getByDocumentAndPrincipal(
            UUID documentId,
            UUID principalId
    );

    List<DocumentPermission> getByPrincipalType(
            UUID documentId,
            DocumentPermission.PrincipalType principalType
    );

    boolean hasPermission(
            UUID documentId,
            UUID principalId,
            DocumentPermission.Permission permission
    );

    List<DocumentPermission> getActivePermissions(
            UUID documentId
    );

    void revokePermission(
            UUID permissionId
    );

    void revokePermissionsForPrincipal(
            UUID documentId,
            UUID principalId
    );

    void revokeAllPermissions(
            UUID documentId
    );
}