package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.FolderPermission;
import com.omragul.knowledge.repository.FolderPermissionRepository;
import com.omragul.knowledge.service.FolderPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FolderPermissionServiceImpl
        implements FolderPermissionService {

    private final FolderPermissionRepository permissionRepository;

    public FolderPermissionServiceImpl(
            FolderPermissionRepository permissionRepository
    ) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    public FolderPermission grantPermission(
            UUID folderId,
            FolderPermission.PrincipalType principalType,
            UUID principalId,
            FolderPermission.Permission permission,
            UUID grantedBy,
            LocalDateTime expiresAt
    ) {

        if (permissionRepository
                .existsByFolderIdAndPrincipalIdAndPermission(
                        folderId,
                        principalId,
                        permission
                )) {

            throw new RuntimeException(
                    "Permission already granted"
            );
        }

        FolderPermission folderPermission =
                new FolderPermission();

        folderPermission.setFolderId(folderId);
        folderPermission.setPrincipalType(principalType);
        folderPermission.setPrincipalId(principalId);
        folderPermission.setPermission(permission);
        folderPermission.setGrantedBy(grantedBy);
        folderPermission.setGrantedAt(
                LocalDateTime.now()
        );
        folderPermission.setExpiresAt(expiresAt);

        return permissionRepository.save(
                folderPermission
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FolderPermission getById(
            UUID permissionId
    ) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Folder permission not found: "
                                        + permissionId
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderPermission> getByFolderId(
            UUID folderId
    ) {
        return permissionRepository.findByFolderId(
                folderId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderPermission> getByPrincipalId(
            UUID principalId
    ) {
        return permissionRepository.findByPrincipalId(
                principalId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderPermission> getByFolderAndPrincipal(
            UUID folderId,
            UUID principalId
    ) {
        return permissionRepository
                .findByFolderIdAndPrincipalId(
                        folderId,
                        principalId
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderPermission> getByPrincipalType(
            UUID folderId,
            FolderPermission.PrincipalType principalType
    ) {
        return permissionRepository
                .findByFolderIdAndPrincipalType(
                        folderId,
                        principalType
                );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(
            UUID folderId,
            UUID principalId,
            FolderPermission.Permission permission
    ) {
        return permissionRepository
                .existsByFolderIdAndPrincipalIdAndPermission(
                        folderId,
                        principalId,
                        permission
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderPermission> getActivePermissions(
            UUID folderId
    ) {
        return permissionRepository.findActivePermissions(
                folderId,
                LocalDateTime.now()
        );
    }

    @Override
    public void revokePermission(
            UUID permissionId
    ) {
        if (!permissionRepository.existsById(permissionId)) {
            throw new RuntimeException(
                    "Folder permission not found: "
                            + permissionId
            );
        }

        permissionRepository.deleteById(permissionId);
    }

    @Override
    public void revokePermissionsForPrincipal(
            UUID folderId,
            UUID principalId
    ) {
        permissionRepository
                .deleteByFolderIdAndPrincipalId(
                        folderId,
                        principalId
                );
    }

    @Override
    public void revokeAllPermissions(
            UUID folderId
    ) {
        permissionRepository.deleteByFolderId(
                folderId
        );
    }
}