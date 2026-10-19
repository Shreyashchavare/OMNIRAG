package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentTagRepository
        extends JpaRepository<DocumentTag, DocumentTag.DocumentTagId> {

    List<DocumentTag> findByDocumentId(
            UUID documentId
    );

    List<DocumentTag> findByTagId(
            UUID tagId
    );

    boolean existsByDocumentIdAndTagId(
            UUID documentId,
            UUID tagId
    );

    void deleteByDocumentIdAndTagId(
            UUID documentId,
            UUID tagId
    );

    void deleteByDocumentId(
            UUID documentId
    );

    void deleteByTagId(
            UUID tagId
    );
}