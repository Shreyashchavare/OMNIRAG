package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.DocumentTagRequest;
import com.omragul.knowledge.dto.DocumentTagResponse;
import com.omragul.knowledge.entity.DocumentTag;
import com.omragul.knowledge.mapper.DocumentTagMapper;
import com.omragul.knowledge.service.DocumentTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-tags")
@Tag(
        name = "Document Tag",
        description = "APIs for assigning and managing tags on documents"
)
public class DocumentTagController {

    private final DocumentTagService documentTagService;

    public DocumentTagController(
            DocumentTagService documentTagService
    ) {
        this.documentTagService = documentTagService;
    }

    // =========================================================
    // ADD TAG
    // =========================================================

    @PostMapping
    @Operation(
            summary = "Add tag to document",
            description = "Assigns a tag to a document"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tag assigned successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    public ResponseEntity<DocumentTagResponse> addTag(
            @Valid @RequestBody DocumentTagRequest request
    ) {

        DocumentTag documentTag =
                documentTagService.addTag(
                        request.getDocumentId(),
                        request.getTagId(),
                        null
                );

        return ResponseEntity.ok(
                DocumentTagMapper.toResponse(documentTag)
        );
    }

    // =========================================================
    // GET TAGS BY DOCUMENT
    // =========================================================

    @GetMapping("/document/{documentId}")
    @Operation(
            summary = "Get tags of document",
            description = "Retrieves all tags assigned to a document"
    )
    public ResponseEntity<List<DocumentTagResponse>> getByDocumentId(
            @PathVariable UUID documentId
    ) {

        List<DocumentTag> documentTags =
                documentTagService.getByDocumentId(documentId);

        return ResponseEntity.ok(
                DocumentTagMapper.toResponseList(documentTags)
        );
    }

    // =========================================================
    // GET DOCUMENTS BY TAG
    // =========================================================

    @GetMapping("/tag/{tagId}")
    @Operation(
            summary = "Get documents by tag",
            description = "Retrieves all document-tag relationships for a tag"
    )
    public ResponseEntity<List<DocumentTagResponse>> getByTagId(
            @PathVariable UUID tagId
    ) {

        List<DocumentTag> documentTags =
                documentTagService.getByTagId(tagId);

        return ResponseEntity.ok(
                DocumentTagMapper.toResponseList(documentTags)
        );
    }

    // =========================================================
    // CHECK TAG ASSIGNMENT
    // =========================================================

    @GetMapping("/document/{documentId}/tag/{tagId}/exists")
    @Operation(
            summary = "Check tag assignment",
            description = "Checks whether a tag is assigned to a document"
    )
    public ResponseEntity<Boolean> exists(
            @PathVariable UUID documentId,
            @PathVariable UUID tagId
    ) {

        return ResponseEntity.ok(
                documentTagService.exists(
                        documentId,
                        tagId
                )
        );
    }

    // =========================================================
    // REMOVE TAG
    // =========================================================

    @DeleteMapping("/document/{documentId}/tag/{tagId}")
    @Operation(
            summary = "Remove tag from document",
            description = "Removes a tag assignment from a document"
    )
    public ResponseEntity<Void> removeTag(
            @PathVariable UUID documentId,
            @PathVariable UUID tagId
    ) {

        documentTagService.removeTag(
                documentId,
                tagId
        );

        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // REMOVE ALL TAGS FROM DOCUMENT
    // =========================================================

    @DeleteMapping("/document/{documentId}")
    @Operation(
            summary = "Remove all tags from document",
            description = "Removes all tag assignments from a document"
    )
    public ResponseEntity<Void> removeAllTagsFromDocument(
            @PathVariable UUID documentId
    ) {

        documentTagService.removeAllTagsFromDocument(
                documentId
        );

        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // REMOVE ALL DOCUMENTS FROM TAG
    // =========================================================

    @DeleteMapping("/tag/{tagId}")
    @Operation(
            summary = "Remove all documents from tag",
            description = "Removes a tag from all associated documents"
    )
    public ResponseEntity<Void> removeAllDocumentsFromTag(
            @PathVariable UUID tagId
    ) {

        documentTagService.removeAllDocumentsFromTag(
                tagId
        );

        return ResponseEntity.noContent().build();
    }
}