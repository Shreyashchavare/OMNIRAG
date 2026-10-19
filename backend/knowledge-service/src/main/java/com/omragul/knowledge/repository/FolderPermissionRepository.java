package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.FolderPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FolderPermissionRepository
        extends JpaRepository<FolderPermission, UUID> {

    List<FolderPermission> findByFolderId(
            UUID folderId
    );

    List<FolderPermission> findByPrincipalId(
            UUID principalId
    );

    List<FolderPermission> findByFolderIdAndPrincipalId(
            UUID folderId,
            UUID principalId
    );

    List<FolderPermission> findByFolderIdAndPrincipalType(
            UUID folderId,
            FolderPermission.PrincipalType principalType
    );

    List<FolderPermission> findByFolderIdAndPrincipalIdAndPermission(
            UUID folderId,
            UUID principalId,
            FolderPermission.Permission permission
    );

    boolean existsByFolderIdAndPrincipalIdAndPermission(
            UUID folderId,
            UUID principalId,
            FolderPermission.Permission permission
    );

    @Query("""
        SELECT p
        FROM FolderPermission p
        WHERE p.folderId = :folderId
          AND (
              p.expiresAt IS NULL
              OR p.expiresAt > :currentTime
          )
    """)
    List<FolderPermission> findActivePermissions(
            @Param("folderId") UUID folderId,
            @Param("currentTime") LocalDateTime currentTime
    );

    void deleteByFolderIdAndPrincipalId(
            UUID folderId,
            UUID principalId
    );

    void deleteByFolderId(
            UUID folderId
    );
}