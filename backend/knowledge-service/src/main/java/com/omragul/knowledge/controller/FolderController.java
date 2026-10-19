package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.FolderResponse;
import com.omragul.knowledge.entity.Folder;
import com.omragul.knowledge.mapper.FolderMapper;
import com.omragul.knowledge.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
@Tag(name = "Folder", description = "APIs for managing folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    // =========================================================
    // CREATE FOLDER
    // =========================================================

    @PostMapping
    @Operation(
            summary = "Create folder",
            description = "Creates a new folder inside a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Folder created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<FolderResponse> createFolder(

            @Parameter(description = "Knowledge base ID", required = true)
            @RequestParam UUID knowledgeBaseId,

            @Parameter(description = "Parent folder ID. Leave empty to create a root folder")
            @RequestParam(required = false) UUID parentFolderId,

            @Parameter(description = "Folder name", required = true)
            @RequestParam String name,

            @Parameter(description = "ID of the folder owner", required = true)
            @RequestHeader("X-User-Id") UUID ownerId
    ) {

        Folder folder = folderService.create(
                knowledgeBaseId,
                parentFolderId,
                name,
                ownerId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(FolderMapper.toResponse(folder));
    }

    // =========================================================
    // GET FOLDER BY ID
    // =========================================================

    @GetMapping("/{id}")
    @Operation(
            summary = "Get folder",
            description = "Returns a folder by its ID"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Folder found"),
            @ApiResponse(responseCode = "404", description = "Folder not found")
    })
    public ResponseEntity<FolderResponse> getFolderById(

            @Parameter(description = "Folder ID", required = true)
            @PathVariable UUID id
    ) {

        Folder folder = folderService.getById(id);

        return ResponseEntity.ok(
                FolderMapper.toResponse(folder)
        );
    }

    // =========================================================
    // GET FOLDERS BY KNOWLEDGE BASE
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}")
    @Operation(
            summary = "Get folders by knowledge base",
            description = "Returns all active folders belonging to a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Folders retrieved successfully")
    })
    public ResponseEntity<List<FolderResponse>> getFoldersByKnowledgeBase(

            @Parameter(description = "Knowledge base ID", required = true)
            @PathVariable UUID knowledgeBaseId
    ) {

        List<Folder> folders =
                folderService.getByKnowledgeBase(knowledgeBaseId);

        return ResponseEntity.ok(
                FolderMapper.toResponseList(folders)
        );
    }

    // =========================================================
    // GET ROOT FOLDERS
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/root")
    @Operation(
            summary = "Get root folders",
            description = "Returns all root-level folders of a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Root folders retrieved successfully")
    })
    public ResponseEntity<List<FolderResponse>> getRootFolders(

            @Parameter(description = "Knowledge base ID", required = true)
            @PathVariable UUID knowledgeBaseId
    ) {

        List<Folder> folders =
                folderService.getRootFolders(knowledgeBaseId);

        return ResponseEntity.ok(
                FolderMapper.toResponseList(folders)
        );
    }

    // =========================================================
    // GET CHILD FOLDERS
    // =========================================================

    @GetMapping("/{id}/children")
    @Operation(
            summary = "Get child folders",
            description = "Returns all child folders of a parent folder"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Child folders retrieved successfully")
    })
    public ResponseEntity<List<FolderResponse>> getChildren(

            @Parameter(description = "Parent folder ID", required = true)
            @PathVariable UUID id
    ) {

        List<Folder> folders =
                folderService.getChildren(id);

        return ResponseEntity.ok(
                FolderMapper.toResponseList(folders)
        );
    }

    // =========================================================
    // UPDATE FOLDER
    // =========================================================

    @PutMapping("/{id}")
    @Operation(
            summary = "Update folder",
            description = "Updates the name of an existing folder"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Folder updated successfully"),
            @ApiResponse(responseCode = "404", description = "Folder not found"),
            @ApiResponse(responseCode = "403", description = "User is not authorized")
    })
    public ResponseEntity<FolderResponse> updateFolder(

            @Parameter(description = "Folder ID", required = true)
            @PathVariable UUID id,

            @Parameter(description = "New folder name", required = true)
            @RequestParam String name,

            @Parameter(description = "ID of the user performing the update", required = true)
            @RequestHeader("X-User-Id") UUID userId
    ) {

        Folder folder = folderService.update(
                id,
                name,
                userId
        );

        return ResponseEntity.ok(
                FolderMapper.toResponse(folder)
        );
    }

    // =========================================================
    // ARCHIVE FOLDER
    // =========================================================

    @PatchMapping("/{id}/archive")
    @Operation(
            summary = "Archive folder",
            description = "Archives an existing folder"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Folder archived successfully"),
            @ApiResponse(responseCode = "404", description = "Folder not found"),
            @ApiResponse(responseCode = "403", description = "User is not authorized")
    })
    public ResponseEntity<FolderResponse> archiveFolder(

            @Parameter(description = "Folder ID", required = true)
            @PathVariable UUID id,

            @Parameter(description = "ID of the user performing the archive", required = true)
            @RequestHeader("X-User-Id") UUID userId
    ) {

        Folder folder = folderService.archive(
                id,
                userId
        );

        return ResponseEntity.ok(
                FolderMapper.toResponse(folder)
        );
    }

    // =========================================================
    // DELETE FOLDER
    // =========================================================

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete folder",
            description = "Soft deletes a folder"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Folder deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Folder not found"),
            @ApiResponse(responseCode = "403", description = "User is not authorized")
    })
    public ResponseEntity<Void> deleteFolder(

            @Parameter(description = "Folder ID", required = true)
            @PathVariable UUID id,

            @Parameter(description = "ID of the user performing the deletion", required = true)
            @RequestHeader("X-User-Id") UUID userId
    ) {

        folderService.delete(
                id,
                userId
        );

        return ResponseEntity.noContent().build();
    }
}