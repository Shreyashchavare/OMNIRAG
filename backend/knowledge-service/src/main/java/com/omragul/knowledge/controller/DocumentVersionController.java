package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.DocumentVersionRequest;
import com.omragul.knowledge.dto.DocumentVersionResponse;
import com.omragul.knowledge.entity.DocumentVersion;
import com.omragul.knowledge.mapper.DocumentVersionMapper;
import com.omragul.knowledge.service.DocumentVersionService;
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
@RequestMapping("/api/v1/document-versions")
@Tag(
        name = "Document Version",
        description = "APIs for managing document versions"
)
public class DocumentVersionController {

    private final DocumentVersionService documentVersionService;

    public DocumentVersionController(
            DocumentVersionService documentVersionService
    ) {
        this.documentVersionService = documentVersionService;
    }

    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping
    @Operation(
            summary = "Create document version",
            description = "Creates a new version of a document"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Document version created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    public ResponseEntity<DocumentVersionResponse> create(
            @Valid @RequestBody DocumentVersionRequest request
    ) {

        DocumentVersion documentVersion =
                DocumentVersionMapper.toEntity(request);

        DocumentVersion created =
                documentVersionService.create(documentVersion);

        return ResponseEntity.ok(
                DocumentVersionMapper.toResponse(created)
        );
    }

    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{versionId}")
    @Operation(
            summary = "Get document version by ID",
            description = "Retrieves a document version using its ID"
    )
    public ResponseEntity<DocumentVersionResponse> getById(
            @PathVariable UUID versionId
    ) {

        DocumentVersion version =
                documentVersionService.getById(versionId);

        return ResponseEntity.ok(
                DocumentVersionMapper.toResponse(version)
        );
    }

    // =========================================================
    // GET ALL VERSIONS
    // =========================================================

    @GetMapping("/document/{documentId}")
    @Operation(
            summary = "Get document versions",
            description = "Retrieves all versions of a document, newest first"
    )
    public ResponseEntity<List<DocumentVersionResponse>> getVersions(
            @PathVariable UUID documentId
    ) {

        List<DocumentVersion> versions =
                documentVersionService.getVersions(documentId);

        return ResponseEntity.ok(
                DocumentVersionMapper.toResponseList(versions)
        );
    }

    // =========================================================
    // GET SPECIFIC VERSION
    // =========================================================

    @GetMapping(
            "/document/{documentId}/version/{versionNumber}"
    )
    @Operation(
            summary = "Get specific document version",
            description = "Retrieves a specific version of a document"
    )
    public ResponseEntity<DocumentVersionResponse> getVersion(
            @PathVariable UUID documentId,
            @PathVariable Integer versionNumber
    ) {

        DocumentVersion version =
                documentVersionService.getVersion(
                        documentId,
                        versionNumber
                );

        return ResponseEntity.ok(
                DocumentVersionMapper.toResponse(version)
        );
    }

    // =========================================================
    // GET LATEST VERSION
    // =========================================================

    @GetMapping("/document/{documentId}/latest")
    @Operation(
            summary = "Get latest document version",
            description = "Retrieves the latest version of a document"
    )
    public ResponseEntity<DocumentVersionResponse> getLatestVersion(
            @PathVariable UUID documentId
    ) {

        DocumentVersion version =
                documentVersionService.getLatestVersion(documentId);

        return ResponseEntity.ok(
                DocumentVersionMapper.toResponse(version)
        );
    }

    // =========================================================
    // GET BY CHECKSUM
    // =========================================================

    @GetMapping("/checksum/{checksum}")
    @Operation(
            summary = "Get version by checksum",
            description = "Retrieves a document version using its file checksum"
    )
    public ResponseEntity<DocumentVersionResponse> getByChecksum(
            @PathVariable String checksum
    ) {

        DocumentVersion version =
                documentVersionService.getByChecksum(checksum);

        return ResponseEntity.ok(
                DocumentVersionMapper.toResponse(version)
        );
    }

    // =========================================================
    // CHECK VERSION EXISTENCE
    // =========================================================

    @GetMapping(
            "/document/{documentId}/exists/{versionNumber}"
    )
    @Operation(
            summary = "Check whether version exists",
            description = "Checks whether a specific document version exists"
    )
    public ResponseEntity<Boolean> existsVersion(
            @PathVariable UUID documentId,
            @PathVariable Integer versionNumber
    ) {

        return ResponseEntity.ok(
                documentVersionService.existsVersion(
                        documentId,
                        versionNumber
                )
        );
    }

    // =========================================================
    // NEXT VERSION NUMBER
    // =========================================================

    @GetMapping("/document/{documentId}/next-version")
    @Operation(
            summary = "Get next version number",
            description = "Returns the next available version number for a document"
    )
    public ResponseEntity<Integer> getNextVersionNumber(
            @PathVariable UUID documentId
    ) {

        return ResponseEntity.ok(
                documentVersionService.getNextVersionNumber(
                        documentId
                )
        );
    }

    // =========================================================
    // COUNT VERSIONS
    // =========================================================

    @GetMapping("/document/{documentId}/count")
    @Operation(
            summary = "Count document versions",
            description = "Returns the total number of versions of a document"
    )
    public ResponseEntity<Long> countVersions(
            @PathVariable UUID documentId
    ) {

        return ResponseEntity.ok(
                documentVersionService.countVersions(documentId)
        );
    }
}