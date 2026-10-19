package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.DocumentMetadataRequest;
import com.omragul.knowledge.dto.DocumentMetadataResponse;
import com.omragul.knowledge.entity.DocumentMetadata;
import com.omragul.knowledge.mapper.DocumentMetadataMapper;
import com.omragul.knowledge.service.DocumentMetadataService;
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
@RequestMapping("/api/v1/document-metadata")
@Tag(
        name = "Document Metadata",
        description = "APIs for managing document metadata"
)
public class DocumentMetadataController {

    private final DocumentMetadataService metadataService;

    public DocumentMetadataController(
            DocumentMetadataService metadataService
    ) {
        this.metadataService = metadataService;
    }

    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping
    @Operation(
            summary = "Create document metadata",
            description = "Creates metadata for a document"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Metadata created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    public ResponseEntity<DocumentMetadataResponse> create(
            @Valid @RequestBody DocumentMetadataRequest request
    ) {

        DocumentMetadata metadata =
                DocumentMetadataMapper.toEntity(request);

        DocumentMetadata created =
                metadataService.create(metadata);

        return ResponseEntity.ok(
                DocumentMetadataMapper.toResponse(created)
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @PutMapping("/{metadataId}")
    @Operation(
            summary = "Update document metadata",
            description = "Updates existing document metadata"
    )
    public ResponseEntity<DocumentMetadataResponse> update(
            @PathVariable UUID metadataId,
            @Valid @RequestBody DocumentMetadataRequest request
    ) {

        DocumentMetadata existing =
                metadataService.getById(metadataId);

        existing.setMetadataKey(
                request.getMetadataKey()
        );

        existing.setMetadataValue(
                request.getMetadataValue()
        );

        existing.setValueType(
                request.getValueType()
        );

        DocumentMetadata updated =
                metadataService.update(existing);

        return ResponseEntity.ok(
                DocumentMetadataMapper.toResponse(updated)
        );
    }

    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{metadataId}")
    @Operation(
            summary = "Get metadata by ID",
            description = "Retrieves document metadata by ID"
    )
    public ResponseEntity<DocumentMetadataResponse> getById(
            @PathVariable UUID metadataId
    ) {

        DocumentMetadata metadata =
                metadataService.getById(metadataId);

        return ResponseEntity.ok(
                DocumentMetadataMapper.toResponse(metadata)
        );
    }

    // =========================================================
    // GET BY DOCUMENT
    // =========================================================

    @GetMapping("/document/{documentId}")
    @Operation(
            summary = "Get document metadata",
            description = "Retrieves all metadata belonging to a document"
    )
    public ResponseEntity<List<DocumentMetadataResponse>> getByDocumentId(
            @PathVariable UUID documentId
    ) {

        List<DocumentMetadata> metadata =
                metadataService.getByDocumentId(documentId);

        return ResponseEntity.ok(
                DocumentMetadataMapper.toResponseList(metadata)
        );
    }

    // =========================================================
    // GET BY DOCUMENT + KEY
    // =========================================================

    @GetMapping("/document/{documentId}/key/{metadataKey}")
    @Operation(
            summary = "Get metadata by key",
            description = "Retrieves a specific metadata entry"
    )
    public ResponseEntity<DocumentMetadataResponse> getByDocumentIdAndKey(
            @PathVariable UUID documentId,
            @PathVariable String metadataKey
    ) {

        DocumentMetadata metadata =
                metadataService.getByDocumentIdAndKey(
                        documentId,
                        metadataKey
                );

        return ResponseEntity.ok(
                DocumentMetadataMapper.toResponse(metadata)
        );
    }

    // =========================================================
    // CHECK EXISTS
    // =========================================================

    @GetMapping("/document/{documentId}/key/{metadataKey}/exists")
    @Operation(
            summary = "Check metadata existence",
            description = "Checks whether metadata exists for a document and key"
    )
    public ResponseEntity<Boolean> exists(
            @PathVariable UUID documentId,
            @PathVariable String metadataKey
    ) {

        return ResponseEntity.ok(
                metadataService.existsByDocumentIdAndKey(
                        documentId,
                        metadataKey
                )
        );
    }

    // =========================================================
    // UPSERT
    // =========================================================

    @PutMapping("/upsert")
    @Operation(
            summary = "Create or update metadata",
            description = "Creates metadata if it does not exist, otherwise updates it"
    )
    public ResponseEntity<DocumentMetadataResponse> upsert(
            @Valid @RequestBody DocumentMetadataRequest request
    ) {

        DocumentMetadata metadata =
                DocumentMetadataMapper.toEntity(request);

        DocumentMetadata result =
                metadataService.upsert(metadata);

        return ResponseEntity.ok(
                DocumentMetadataMapper.toResponse(result)
        );
    }

    // =========================================================
    // DELETE
    // =========================================================

    @DeleteMapping("/{metadataId}")
    @Operation(
            summary = "Delete metadata",
            description = "Deletes metadata by ID"
    )
    public ResponseEntity<Void> delete(
            @PathVariable UUID metadataId
    ) {

        metadataService.delete(metadataId);

        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // DELETE BY DOCUMENT + KEY
    // =========================================================

    @DeleteMapping("/document/{documentId}/key/{metadataKey}")
    @Operation(
            summary = "Delete metadata by key",
            description = "Deletes metadata using document ID and metadata key"
    )
    public ResponseEntity<Void> deleteByDocumentIdAndKey(
            @PathVariable UUID documentId,
            @PathVariable String metadataKey
    ) {

        metadataService.deleteByDocumentIdAndKey(
                documentId,
                metadataKey
        );

        return ResponseEntity.noContent().build();
    }
}