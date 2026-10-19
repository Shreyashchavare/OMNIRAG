package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.Folder;
import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.repository.FolderRepository;
import com.omragul.knowledge.repository.KnowledgeBaseRepository;
import com.omragul.knowledge.service.FolderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public FolderServiceImpl(
            FolderRepository folderRepository,
            KnowledgeBaseRepository knowledgeBaseRepository
    ) {
        this.folderRepository = folderRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @Override
    public Folder create(
            UUID knowledgeBaseId,
            UUID parentFolderId,
            String name,
            UUID ownerId
    ) {

        validateRequiredIds(
                knowledgeBaseId,
                ownerId
        );

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Folder name is required"
            );
        }

        name = name.trim();

        KnowledgeBase knowledgeBase =
                knowledgeBaseRepository
                        .findById(knowledgeBaseId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Knowledge base not found: "
                                                + knowledgeBaseId
                                )
                        );

        if (knowledgeBase.getStatus()
                != KnowledgeBase.Status.ACTIVE) {

            throw new IllegalArgumentException(
                    "Knowledge base is not active"
            );
        }

        Folder parent = null;

        if (parentFolderId != null) {

            parent = folderRepository.findById(
                    parentFolderId
            ).orElseThrow(() ->
                    new IllegalArgumentException(
                            "Parent folder not found: "
                                    + parentFolderId
                    )
            );

            if (parent.getStatus()
                    != Folder.Status.ACTIVE) {

                throw new IllegalArgumentException(
                        "Parent folder is not active"
                );
            }

            if (!parent.getKnowledgeBaseId()
                    .equals(knowledgeBaseId)) {

                throw new IllegalArgumentException(
                        "Parent folder belongs to another knowledge base"
                );
            }
        }

        if (folderRepository
                .existsByKnowledgeBaseIdAndParentFolderIdAndNameAndStatus(
                        knowledgeBaseId,
                        parentFolderId,
                        name,
                        Folder.Status.ACTIVE
                )) {

            throw new IllegalArgumentException(
                    "Folder with this name already exists"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        Folder folder = new Folder();

        folder.setKnowledgeBaseId(knowledgeBaseId);
        folder.setParentFolderId(parentFolderId);
        folder.setName(name);

        if (parent == null) {
            folder.setPath("/" + name);
            folder.setLevel(0);
        } else {
            folder.setPath(
                    parent.getPath() + "/" + name
            );
            folder.setLevel(
                    parent.getLevel() + 1
            );

            parent.setTotalSubfolders(
                    parent.getTotalSubfolders() + 1
            );

            parent.setUpdatedAt(now);

            folderRepository.save(parent);
        }

        folder.setOwnerId(ownerId);

        folder.setStatus(Folder.Status.ACTIVE);

        folder.setTotalDocuments(0);
        folder.setTotalSubfolders(0);
        folder.setTotalSizeBytes(0L);

        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);

        folder.setCreatedBy(ownerId);
        folder.setUpdatedBy(ownerId);

        folder.setVersion(1);

        return folderRepository.save(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public Folder getById(UUID id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Folder ID cannot be null"
            );
        }

        return folderRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Folder not found: " + id
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Folder getActiveById(UUID id) {

        Folder folder = getById(id);

        if (folder.getStatus()
                != Folder.Status.ACTIVE) {

            throw new IllegalArgumentException(
                    "Folder is not active"
            );
        }

        return folder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Folder> getByKnowledgeBase(
            UUID knowledgeBaseId
    ) {

        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException(
                    "Knowledge base ID cannot be null"
            );
        }

        return folderRepository
                .findByKnowledgeBaseIdAndStatus(
                        knowledgeBaseId,
                        Folder.Status.ACTIVE
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Folder> getRootFolders(
            UUID knowledgeBaseId
    ) {

        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException(
                    "Knowledge base ID cannot be null"
            );
        }

        return folderRepository
                .findByKnowledgeBaseIdAndParentFolderIdIsNull(
                        knowledgeBaseId
                )
                .stream()
                .filter(folder ->
                        folder.getStatus()
                                == Folder.Status.ACTIVE
                )
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Folder> getChildren(
            UUID parentFolderId
    ) {

        if (parentFolderId == null) {
            throw new IllegalArgumentException(
                    "Parent folder ID cannot be null"
            );
        }

        return folderRepository
                .findByKnowledgeBaseIdAndParentFolderIdAndStatus(
                        getById(parentFolderId)
                                .getKnowledgeBaseId(),
                        parentFolderId,
                        Folder.Status.ACTIVE
                );
    }

    @Override
    public Folder update(
            UUID id,
            String name,
            UUID userId
    ) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        Folder folder = getActiveById(id);

        if (!folder.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException(
                    "User is not the owner of this folder"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Folder name is required"
            );
        }

        name = name.trim();

        if (!name.equals(folder.getName())) {

            if (folderRepository
                    .existsByKnowledgeBaseIdAndParentFolderIdAndNameAndStatus(
                            folder.getKnowledgeBaseId(),
                            folder.getParentFolderId(),
                            name,
                            Folder.Status.ACTIVE
                    )) {

                throw new IllegalArgumentException(
                        "Folder with this name already exists"
                );
            }

            String oldPath = folder.getPath();
            String newPath;

            if (folder.getParentFolderId() == null) {
                newPath = "/" + name;
            } else {
                Folder parent = getActiveById(
                        folder.getParentFolderId()
                );

                newPath = parent.getPath() + "/" + name;
            }

            folder.setName(name);
            folder.setPath(newPath);

            updateDescendantPaths(
                    folder.getId(),
                    oldPath,
                    newPath
            );
        }

        folder.setUpdatedBy(userId);
        folder.setUpdatedAt(LocalDateTime.now());
        folder.setVersion(
                folder.getVersion() + 1
        );

        return folderRepository.save(folder);
    }

    @Override
    public Folder archive(
            UUID id,
            UUID userId
    ) {

        validateUserId(userId);

        Folder folder = getActiveById(id);

        verifyOwner(folder, userId);

        folder.setStatus(
                Folder.Status.ARCHIVED
        );

        LocalDateTime now = LocalDateTime.now();

        folder.setUpdatedBy(userId);
        folder.setUpdatedAt(now);
        folder.setVersion(
                folder.getVersion() + 1
        );

        return folderRepository.save(folder);
    }

    @Override
    public Folder delete(
            UUID id,
            UUID userId
    ) {

        validateUserId(userId);

        Folder folder = getActiveById(id);

        verifyOwner(folder, userId);

        LocalDateTime now = LocalDateTime.now();

        folder.setStatus(
                Folder.Status.DELETED
        );

        folder.setDeletedAt(now);
        folder.setUpdatedBy(userId);
        folder.setUpdatedAt(now);

        folder.setVersion(
                folder.getVersion() + 1
        );

        if (folder.getParentFolderId() != null) {

            Folder parent = getById(
                    folder.getParentFolderId()
            );

            if (parent.getTotalSubfolders() > 0) {
                parent.setTotalSubfolders(
                        parent.getTotalSubfolders() - 1
                );
            }

            parent.setUpdatedAt(now);

            folderRepository.save(parent);
        }

        return folderRepository.save(folder);
    }

    private void updateDescendantPaths(
            UUID folderId,
            String oldPath,
            String newPath
    ) {

        List<Folder> children =
                folderRepository.findByParentFolderId(
                        folderId
                );

        for (Folder child : children) {

            if (child.getStatus()
                    == Folder.Status.DELETED) {
                continue;
            }

            String childPath =
                    child.getPath();

            if (childPath.startsWith(oldPath)) {

                child.setPath(
                        newPath
                                + childPath.substring(
                                oldPath.length()
                        )
                );

                child.setUpdatedAt(
                        LocalDateTime.now()
                );

                folderRepository.save(child);

                updateDescendantPaths(
                        child.getId(),
                        oldPath,
                        newPath
                );
            }
        }
    }

    private void verifyOwner(
            Folder folder,
            UUID userId
    ) {

        if (!folder.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException(
                    "User is not the owner of this folder"
            );
        }
    }

    private void validateRequiredIds(
            UUID knowledgeBaseId,
            UUID ownerId
    ) {

        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException(
                    "Knowledge base ID cannot be null"
            );
        }

        validateUserId(ownerId);
    }

    private void validateUserId(UUID userId) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }
    }
}