package com.omragul.knowledge.repository;

import com.omragul.knowledge.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findByKnowledgeBaseId(UUID knowledgeBaseId);

    List<Folder> findByKnowledgeBaseIdAndStatus(
            UUID knowledgeBaseId,
            Folder.Status status
    );

    List<Folder> findByParentFolderId(UUID parentFolderId);

    List<Folder> findByKnowledgeBaseIdAndParentFolderIdIsNull(
            UUID knowledgeBaseId
    );

    List<Folder> findByKnowledgeBaseIdAndParentFolderIdAndStatus(
            UUID knowledgeBaseId,
            UUID parentFolderId,
            Folder.Status status
    );

    Optional<Folder> findByKnowledgeBaseIdAndParentFolderIdAndName(
            UUID knowledgeBaseId,
            UUID parentFolderId,
            String name
    );

    boolean existsByKnowledgeBaseIdAndParentFolderIdAndName(
            UUID knowledgeBaseId,
            UUID parentFolderId,
            String name
    );

    boolean existsByKnowledgeBaseIdAndParentFolderIdAndNameAndStatus(
            UUID knowledgeBaseId,
            UUID parentFolderId,
            String name,
            Folder.Status status
    );
}