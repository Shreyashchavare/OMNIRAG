package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.DocumentRequest;
import com.omragul.knowledge.dto.DocumentResponse;
import com.omragul.knowledge.dto.DocumentUpdateRequest;
import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.mapper.DocumentMapper;
import com.omragul.knowledge.service.DocumentService;
import com.omragul.knowledge.exception.UnauthorizedException;
import com.omragul.knowledge.security.InternalAuthService;
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
    private final InternalAuthService internalAuthService;

    public DocumentController(
            DocumentService documentService,
            InternalAuthService internalAuthService
    ) {
        this.documentService = documentService;
        this.internalAuthService = internalAuthService;
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
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Knowledge base not found")
    })
    public ResponseEntity<DocumentResponse> create(
            @Valid @RequestBody DocumentRequest request,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Document document = DocumentMapper.toEntity(request);
        document.setOwnerId(userId);

        Document created = documentService.create(document, userId);

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
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<DocumentResponse> getById(
            @Parameter(description = "Document ID")
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String internalSecret,
            @RequestHeader(value = "X-Internal-Service-Secret", required = false) String internalServiceSecret
    ) {
        String effectiveSecret = (internalSecret != null && !internalSecret.isBlank()) ? internalSecret : internalServiceSecret;
        boolean isInternal = internalAuthService.isAuthorizedInternalService(effectiveSecret);
        if (!isInternal && userId == null) {
            throw new UnauthorizedException("Authentication required: missing user identity or internal service credentials");
        }
        Document document = documentService.getById(id, userId, isInternal);

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
            @PathVariable UUID documentId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Document document =
                documentService.getByKnowledgeBaseAndId(
                        knowledgeBaseId,
                        documentId,
                        userId
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
            @PathVariable UUID knowledgeBaseId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        List<Document> documents =
                documentService.getByKnowledgeBase(knowledgeBaseId, userId);

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
            Pageable pageable,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Page<Document> documents =
                documentService.getByKnowledgeBase(
                        knowledgeBaseId,
                        userId,
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
            @PathVariable Document.Status status,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        List<Document> documents =
                documentService.getByKnowledgeBaseAndStatus(
                        knowledgeBaseId,
                        status,
                        userId
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
            Pageable pageable,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Page<Document> documents =
                documentService.getByKnowledgeBaseAndStatus(
                        knowledgeBaseId,
                        status,
                        userId,
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
            @PathVariable Document.ProcessingStatus processingStatus,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        List<Document> documents =
                documentService.getByProcessingStatus(
                        knowledgeBaseId,
                        processingStatus,
                        userId
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
            @PathVariable UUID folderId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        List<Document> documents =
                documentService.getByFolder(folderId, userId);

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
            Pageable pageable,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Page<Document> documents =
                documentService.getByFolder(
                        folderId,
                        userId,
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
            @PathVariable UUID categoryId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        List<Document> documents =
                documentService.getByCategory(categoryId, userId);

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
            Pageable pageable,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Page<Document> documents =
                documentService.getByCategory(
                        categoryId,
                        userId,
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
            @PathVariable UUID ownerId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        List<Document> documents =
                documentService.getByOwner(ownerId, userId);

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
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<DocumentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUpdateRequest request,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Document document = new Document();

        DocumentMapper.updateEntity(
                document,
                request
        );

        Document updated =
                documentService.update(
                        id,
                        document,
                        userId
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
            description = "Updates the processing status of a document (restricted to internal services)"
    )
    public ResponseEntity<DocumentResponse> updateProcessingStatus(
            @PathVariable UUID id,
            @PathVariable Document.ProcessingStatus processingStatus,
            @RequestHeader(value = "X-Internal-Secret", required = false) String internalSecret,
            @RequestHeader(value = "X-Internal-Service-Secret", required = false) String internalServiceSecret
    ) {
        String effectiveSecret = (internalSecret != null && !internalSecret.isBlank()) ? internalSecret : internalServiceSecret;
        if (!internalAuthService.isAuthorizedInternalService(effectiveSecret)) {
            throw new UnauthorizedException("Access denied: Invalid or missing internal service credentials");
        }
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
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Document archived =
                documentService.archive(id, userId);

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
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Document restored =
                documentService.restore(id, userId);

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
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        Document deleted =
                documentService.softDelete(id, userId);

        return ResponseEntity.ok(
                DocumentMapper.toResponse(deleted)
        );
    }
}