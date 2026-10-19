package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.DocumentShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DocumentShareRepository
        extends JpaRepository<DocumentShare, UUID> {

    List<DocumentShare> findByDocumentId(
            UUID documentId
    );

    List<DocumentShare> findBySharedWith(
            UUID sharedWith
    );

    List<DocumentShare> findBySharedBy(
            UUID sharedBy
    );

    List<DocumentShare> findByDocumentIdAndSharedWith(
            UUID documentId,
            UUID sharedWith
    );

    boolean existsByDocumentIdAndSharedWith(
            UUID documentId,
            UUID sharedWith
    );

    @Query("""
        SELECT s
        FROM DocumentShare s
        WHERE s.documentId = :documentId
          AND (
              s.expiresAt IS NULL
              OR s.expiresAt > :currentTime
          )
    """)
    List<DocumentShare> findActiveShares(
            @Param("documentId") UUID documentId,
            @Param("currentTime") LocalDateTime currentTime
    );

    @Query("""
        SELECT s
        FROM DocumentShare s
        WHERE s.sharedWith = :userId
          AND (
              s.expiresAt IS NULL
              OR s.expiresAt > :currentTime
          )
    """)
    List<DocumentShare> findActiveSharesForUser(
            @Param("userId") UUID userId,
            @Param("currentTime") LocalDateTime currentTime
    );

    void deleteByDocumentIdAndSharedWith(
            UUID documentId,
            UUID sharedWith
    );

    void deleteByDocumentId(
            UUID documentId
    );
}