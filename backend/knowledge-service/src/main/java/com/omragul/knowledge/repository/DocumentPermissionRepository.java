package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.DocumentPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DocumentPermissionRepository
        extends JpaRepository<DocumentPermission, UUID> {

    List<DocumentPermission> findByDocumentId(
            UUID documentId
    );

    List<DocumentPermission> findByPrincipalId(
            UUID principalId
    );

    List<DocumentPermission> findByDocumentIdAndPrincipalId(
            UUID documentId,
            UUID principalId
    );

    List<DocumentPermission> findByDocumentIdAndPrincipalType(
            UUID documentId,
            DocumentPermission.PrincipalType principalType
    );

    List<DocumentPermission> findByDocumentIdAndPrincipalIdAndPermission(
            UUID documentId,
            UUID principalId,
            DocumentPermission.Permission permission
    );

    boolean existsByDocumentIdAndPrincipalIdAndPermission(
            UUID documentId,
            UUID principalId,
            DocumentPermission.Permission permission
    );

    @Query("""
    SELECT p
    FROM DocumentPermission p
    WHERE p.documentId = :documentId
      AND (
          p.expiresAt IS NULL
          OR p.expiresAt > :currentTime
      )
""")
    List<DocumentPermission> findActivePermissions(
            @Param("documentId") UUID documentId,
            @Param("currentTime") LocalDateTime currentTime
    );

    void deleteByDocumentIdAndPrincipalId(
            UUID documentId,
            UUID principalId
    );

    void deleteByDocumentId(
            UUID documentId
    );
}