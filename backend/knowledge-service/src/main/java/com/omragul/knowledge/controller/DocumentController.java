package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.DocumentRequest;
import com.omragul.knowledge.dto.DocumentResponse;
import com.omragul.knowledge.dto.DocumentUpdateRequest;
import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.mapper.DocumentMapper;
import com.omragul.knowledge.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(
        name = "Document",
        description = "APIs for managing documents"
)
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping
    @Operation(
            summary = "Create document",
            description = "Creates a new document in a knowledge base"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody DocumentRequest request
    ) {
        Document document = DocumentMapper.toEntity(request);

        Document created = documentService.create(document);

        return ResponseEntity.ok(
                DocumentMapper.toResponse(created)
        );
    }

    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{id}")
    @Operation(
            summary = "Get document by ID",
            description = "Retrieves a document using its ID"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<DocumentResponse> getById(
            @Parameter(description = "Document ID")
            @PathVariable UUID id
    ) {
        Document document = documentService.getById(id);

        return ResponseEntity.ok(
                DocumentMapper.toResponse(document)
        );
    }

    // =========================================================
    // GET BY KNOWLEDGE BASE + DOCUMENT ID
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/{documentId}")
    @Operation(
            summary = "Get document from knowledge base",
            description = "Retrieves a specific document belonging to a knowledge base"
    )
    public ResponseEntity<DocumentResponse> getByKnowledgeBaseAndId(
            @PathVariable UUID knowledgeBaseId,
            @PathVariable UUID documentId
    ) {
        Document document =
                documentService.getByKnowledgeBaseAndId(
                        knowledgeBaseId,
                        documentId
                );

        return ResponseEntity.ok(
                DocumentMapper.toResponse(document)
        );
    }

    // =========================================================
    // GET ALL DOCUMENTS BY KNOWLEDGE BASE
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}")
    @Operation(
            summary = "Get documents by knowledge base",
            description = "Retrieves all documents belonging to a knowledge base"
    )
    public ResponseEntity<List<DocumentResponse>> getByKnowledgeBase(
            @PathVariable UUID knowledgeBaseId
    ) {
        List<Document> documents =
                documentService.getByKnowledgeBase(knowledgeBaseId);

        return ResponseEntity.ok(
                DocumentMapper.toResponseList(documents)
        );
    }

    // =========================================================
    // GET PAGINATED DOCUMENTS BY KNOWLEDGE BASE
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/page")
    @Operation(
            summary = "Get paginated documents by knowledge base",
            description = "Retrieves paginated documents belonging to a knowledge base"
    )
    public ResponseEntity<Page<DocumentResponse>> getByKnowledgeBase(
            @PathVariable UUID knowledgeBaseId,
            Pageable pageable
    ) {
        Page<Document> documents =
                documentService.getByKnowledgeBase(
                        knowledgeBaseId,
                        pageable
                );

        return ResponseEntity.ok(
                documents.map(DocumentMapper::toResponse)
        );
    }

    // =========================================================
    // GET DOCUMENTS BY STATUS
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/status/{status}")
    @Operation(
            summary = "Get documents by status",
            description = "Retrieves documents from a knowledge base filtered by status"
    )
    public ResponseEntity<List<DocumentResponse>> getByKnowledgeBaseAndStatus(
            @PathVariable UUID knowledgeBaseId,
            @PathVariable Document.Status status
    ) {
        List<Document> documents =
                documentService.getByKnowledgeBaseAndStatus(
                        knowledgeBaseId,
                        status
                );

        return ResponseEntity.ok(
                DocumentMapper.toResponseList(documents)
        );
    }

    // =========================================================
    // GET PAGINATED DOCUMENTS BY STATUS
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/status/{status}/page")
    @Operation(
            summary = "Get paginated documents by status",
            description = "Retrieves paginated documents filtered by status"
    )
    public ResponseEntity<Page<DocumentResponse>> getByKnowledgeBaseAndStatus(
            @PathVariable UUID knowledgeBaseId,
            @PathVariable Document.Status status,
            Pageable pageable
    ) {
        Page<Document> documents =
                documentService.getByKnowledgeBaseAndStatus(
                        knowledgeBaseId,
                        status,
                        pageable
                );

        return ResponseEntity.ok(
                documents.map(DocumentMapper::toResponse)
        );
    }

    // =========================================================
    // GET DOCUMENTS BY PROCESSING STATUS
    // =========================================================

    @GetMapping("/knowledge-base/{knowledgeBaseId}/processing-status/{processingStatus}")
    @Operation(
            summary = "Get documents by processing status",
            description = "Retrieves documents filtered by processing status"
    )
    public ResponseEntity<List<DocumentResponse>> getByProcessingStatus(
            @PathVariable UUID knowledgeBaseId,
            @PathVariable Document.ProcessingStatus processingStatus
    ) {
        List<Document> documents =
                documentService.getByProcessingStatus(
                        knowledgeBaseId,
                        processingStatus
                );

        return ResponseEntity.ok(
                DocumentMapper.toResponseList(documents)
        );
    }

    // =========================================================
    // GET DOCUMENTS BY FOLDER
    // =========================================================

    @GetMapping("/folder/{folderId}")
    @Operation(
            summary = "Get documents by folder",
            description = "Retrieves all documents belonging to a folder"
    )
    public ResponseEntity<List<DocumentResponse>> getByFolder(
            @PathVariable UUID folderId
    ) {
        List<Document> documents =
                documentService.getByFolder(folderId);

        return ResponseEntity.ok(
                DocumentMapper.toResponseList(documents)
        );
    }

    // =========================================================
    // GET PAGINATED DOCUMENTS BY FOLDER
    // =========================================================

    @GetMapping("/folder/{folderId}/page")
    @Operation(
            summary = "Get paginated documents by folder",
            description = "Retrieves paginated documents belonging to a folder"
    )
    public ResponseEntity<Page<DocumentResponse>> getByFolder(
            @PathVariable UUID folderId,
            Pageable pageable
    ) {
        Page<Document> documents =
                documentService.getByFolder(
                        folderId,
                        pageable
                );

        return ResponseEntity.ok(
                documents.map(DocumentMapper::toResponse)
        );
    }

    // =========================================================
    // GET DOCUMENTS BY CATEGORY
    // =========================================================

    @GetMapping("/category/{categoryId}")
    @Operation(
            summary = "Get documents by category",
            description = "Retrieves all documents belonging to a category"
    )
    public ResponseEntity<List<DocumentResponse>> getByCategory(
            @PathVariable UUID categoryId
    ) {
        List<Document> documents =
                documentService.getByCategory(categoryId);

        return ResponseEntity.ok(
                DocumentMapper.toResponseList(documents)
        );
    }

    // =========================================================
    // GET PAGINATED DOCUMENTS BY CATEGORY
    // =========================================================

    @GetMapping("/category/{categoryId}/page")
    @Operation(
            summary = "Get paginated documents by category",
            description = "Retrieves paginated documents belonging to a category"
    )
    public ResponseEntity<Page<DocumentResponse>> getByCategory(
            @PathVariable UUID categoryId,
            Pageable pageable
    ) {
        Page<Document> documents =
                documentService.getByCategory(
                        categoryId,
                        pageable
                );

        return ResponseEntity.ok(
                documents.map(DocumentMapper::toResponse)
        );
    }

    // =========================================================
    // GET DOCUMENTS BY OWNER
    // =========================================================

    @GetMapping("/owner/{ownerId}")
    @Operation(
            summary = "Get documents by owner",
            description = "Retrieves all documents owned by a user"
    )
    public ResponseEntity<List<DocumentResponse>> getByOwner(
            @PathVariable UUID ownerId
    ) {
        List<Document> documents =
                documentService.getByOwner(ownerId);

        return ResponseEntity.ok(
                DocumentMapper.toResponseList(documents)
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @PutMapping("/{id}")
    @Operation(
            summary = "Update document",
            description = "Updates editable information of an existing document"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<DocumentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUpdateRequest request
    ) {
        Document document = new Document();

        DocumentMapper.updateEntity(
                document,
                request
        );

        Document updated =
                documentService.update(
                        id,
                        document
                );

        return ResponseEntity.ok(
                DocumentMapper.toResponse(updated)
        );
    }

    // =========================================================
    // UPDATE PROCESSING STATUS
    // =========================================================

    @PatchMapping("/{id}/processing-status/{processingStatus}")
    @Operation(
            summary = "Update document processing status",
            description = "Updates the processing status of a document"
    )
    public ResponseEntity<DocumentResponse> updateProcessingStatus(
            @PathVariable UUID id,
            @PathVariable Document.ProcessingStatus processingStatus
    ) {
        Document updated =
                documentService.updateProcessingStatus(
                        id,
                        processingStatus
                );

        return ResponseEntity.ok(
                DocumentMapper.toResponse(updated)
        );
    }

    // =========================================================
    // ARCHIVE
    // =========================================================

    @PatchMapping("/{id}/archive")
    @Operation(
            summary = "Archive document",
            description = "Archives an active document"
    )
    public ResponseEntity<DocumentResponse> archive(
            @PathVariable UUID id
    ) {
        Document archived =
                documentService.archive(id);

        return ResponseEntity.ok(
                DocumentMapper.toResponse(archived)
        );
    }

    // =========================================================
    // RESTORE
    // =========================================================

    @PatchMapping("/{id}/restore")
    @Operation(
            summary = "Restore document",
            description = "Restores an archived or deleted document"
    )
    public ResponseEntity<DocumentResponse> restore(
            @PathVariable UUID id
    ) {
        Document restored =
                documentService.restore(id);

        return ResponseEntity.ok(
                DocumentMapper.toResponse(restored)
        );
    }

    // =========================================================
    // SOFT DELETE
    // =========================================================

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete document",
            description = "Soft deletes a document"
    )
    public ResponseEntity<DocumentResponse> delete(
            @PathVariable UUID id
    ) {
        Document deleted =
                documentService.softDelete(id);

        return ResponseEntity.ok(
                DocumentMapper.toResponse(deleted)
        );
    }
}