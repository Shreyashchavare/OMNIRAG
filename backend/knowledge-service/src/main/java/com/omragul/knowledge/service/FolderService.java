package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.Folder;

import java.util.List;
import java.util.UUID;

public interface FolderService {

    Folder create(
            UUID knowledgeBaseId,
            UUID parentFolderId,
            String name,
            UUID ownerId
    );

    Folder getById(
            UUID id
    );

    Folder getActiveById(
            UUID id
    );

    List<Folder> getByKnowledgeBase(
            UUID knowledgeBaseId
    );

    List<Folder> getRootFolders(
            UUID knowledgeBaseId
    );

    List<Folder> getChildren(
            UUID parentFolderId
    );

    Folder update(
            UUID id,
            String name,
            UUID userId
    );

    Folder archive(
            UUID id,
            UUID userId
    );

    Folder delete(
            UUID id,
            UUID userId
    );
}