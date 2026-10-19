package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.DocumentPermission;
import com.omragul.knowledge.repository.DocumentPermissionRepository;
import com.omragul.knowledge.service.DocumentPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DocumentPermissionServiceImpl
        implements DocumentPermissionService {

    private final DocumentPermissionRepository permissionRepository;

    public DocumentPermissionServiceImpl(
            DocumentPermissionRepository permissionRepository
    ) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    public DocumentPermission grantPermission(
            UUID documentId,
            DocumentPermission.PrincipalType principalType,
            UUID principalId,
            DocumentPermission.Permission permission,
            UUID grantedBy,
            LocalDateTime expiresAt
    ) {

        if (permissionRepository
                .existsByDocumentIdAndPrincipalIdAndPermission(
                        documentId,
                        principalId,
                        permission
                )) {

            throw new RuntimeException(
                    "Permission already granted"
            );
        }

        DocumentPermission documentPermission =
                new DocumentPermission();

        documentPermission.setDocumentId(documentId);
        documentPermission.setPrincipalType(principalType);
        documentPermission.setPrincipalId(principalId);
        documentPermission.setPermission(permission);
        documentPermission.setGrantedBy(grantedBy);
        documentPermission.setGrantedAt(
                LocalDateTime.now()
        );
        documentPermission.setExpiresAt(expiresAt);

        return permissionRepository.save(
                documentPermission
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentPermission getById(
            UUID permissionId
    ) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document permission not found: "
                                        + permissionId
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentPermission> getByDocumentId(
            UUID documentId
    ) {
        return permissionRepository.findByDocumentId(
                documentId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentPermission> getByPrincipalId(
            UUID principalId
    ) {
        return permissionRepository.findByPrincipalId(
                principalId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentPermission> getByDocumentAndPrincipal(
            UUID documentId,
            UUID principalId
    ) {
        return permissionRepository
                .findByDocumentIdAndPrincipalId(
                        documentId,
                        principalId
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentPermission> getByPrincipalType(
            UUID documentId,
            DocumentPermission.PrincipalType principalType
    ) {
        return permissionRepository
                .findByDocumentIdAndPrincipalType(
                        documentId,
                        principalType
                );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(
            UUID documentId,
            UUID principalId,
            DocumentPermission.Permission permission
    ) {
        return permissionRepository
                .existsByDocumentIdAndPrincipalIdAndPermission(
                        documentId,
                        principalId,
                        permission
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentPermission> getActivePermissions(
            UUID documentId
    ) {
        return permissionRepository
                .findActivePermissions(
                        documentId,
                        LocalDateTime.now()
                );
    }

    @Override
    public void revokePermission(
            UUID permissionId
    ) {
        if (!permissionRepository.existsById(permissionId)) {
            throw new RuntimeException(
                    "Document permission not found: "
                            + permissionId
            );
        }

        permissionRepository.deleteById(permissionId);
    }

    @Override
    public void revokePermissionsForPrincipal(
            UUID documentId,
            UUID principalId
    ) {
        permissionRepository
                .deleteByDocumentIdAndPrincipalId(
                        documentId,
                        principalId
                );
    }

    @Override
    public void revokeAllPermissions(
            UUID documentId
    ) {
        permissionRepository.deleteByDocumentId(
                documentId
        );
    }
}