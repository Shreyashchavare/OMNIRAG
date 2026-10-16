package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.DocumentShareRequest;
import com.omragul.knowledge.dto.DocumentShareResponse;
import com.omragul.knowledge.entity.DocumentShare;
import com.omragul.knowledge.mapper.DocumentShareMapper;
import com.omragul.knowledge.service.DocumentShareService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-shares")
@Tag(
        name = "Document Sharing",
        description = "APIs for sharing of documents"
)
public class DocumentShareController {

    private final DocumentShareService documentShareService;

    public DocumentShareController(
            DocumentShareService documentShareService
    ) {
        this.documentShareService = documentShareService;
    }

    // ---------------------------------------------------------
    // Create Share
    // ---------------------------------------------------------

    @PostMapping
    public ResponseEntity<DocumentShareResponse> createShare(
            @Valid @RequestBody DocumentShareRequest request
    ) {

        DocumentShare share =
                documentShareService.createShare(
                        request.getDocumentId(),
                        null,
                        request.getSharedWith(),
                        request.getAccessLevel(),
                        request.getExpiresAt()
                );

        return ResponseEntity.ok(
                DocumentShareMapper.toResponse(share)
        );
    }

    // ---------------------------------------------------------
    // Get Share By ID
    // ---------------------------------------------------------

    @GetMapping("/{shareId}")
    public ResponseEntity<DocumentShareResponse> getById(
            @PathVariable UUID shareId
    ) {

        DocumentShare share =
                documentShareService.getById(shareId);

        return ResponseEntity.ok(
                DocumentShareMapper.toResponse(share)
        );
    }

    // ---------------------------------------------------------
    // Get All Shares For Document
    // ---------------------------------------------------------

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<DocumentShareResponse>> getByDocumentId(
            @PathVariable UUID documentId
    ) {

        List<DocumentShare> shares =
                documentShareService.getByDocumentId(documentId);

        return ResponseEntity.ok(
                DocumentShareMapper.toResponseList(shares)
        );
    }

    // ---------------------------------------------------------
    // Get Shares For User
    // ---------------------------------------------------------

    @GetMapping("/user/{sharedWith}")
    public ResponseEntity<List<DocumentShareResponse>> getBySharedWith(
            @PathVariable UUID sharedWith
    ) {

        List<DocumentShare> shares =
                documentShareService.getBySharedWith(sharedWith);

        return ResponseEntity.ok(
                DocumentShareMapper.toResponseList(shares)
        );
    }

    // ---------------------------------------------------------
    // Get Shares Created By User
    // ---------------------------------------------------------

    @GetMapping("/shared-by/{sharedBy}")
    public ResponseEntity<List<DocumentShareResponse>> getBySharedBy(
            @PathVariable UUID sharedBy
    ) {

        List<DocumentShare> shares =
                documentShareService.getBySharedBy(sharedBy);

        return ResponseEntity.ok(
                DocumentShareMapper.toResponseList(shares)
        );
    }

    // ---------------------------------------------------------
    // Get Share For Document + User
    // ---------------------------------------------------------

    @GetMapping("/document/{documentId}/user/{sharedWith}")
    public ResponseEntity<List<DocumentShareResponse>>
    getByDocumentAndUser(
            @PathVariable UUID documentId,
            @PathVariable UUID sharedWith
    ) {

        List<DocumentShare> shares =
                documentShareService.getByDocumentAndUser(
                        documentId,
                        sharedWith
                );

        return ResponseEntity.ok(
                DocumentShareMapper.toResponseList(shares)
        );
    }

    // ---------------------------------------------------------
    // Get Active Shares For Document
    // ---------------------------------------------------------

    @GetMapping("/document/{documentId}/active")
    public ResponseEntity<List<DocumentShareResponse>>
    getActiveShares(
            @PathVariable UUID documentId
    ) {

        List<DocumentShare> shares =
                documentShareService.getActiveShares(documentId);

        return ResponseEntity.ok(
                DocumentShareMapper.toResponseList(shares)
        );
    }

    // ---------------------------------------------------------
    // Get Active Shares For User
    // ---------------------------------------------------------

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<List<DocumentShareResponse>>
    getActiveSharesForUser(
            @PathVariable UUID userId
    ) {

        List<DocumentShare> shares =
                documentShareService.getActiveSharesForUser(userId);

        return ResponseEntity.ok(
                DocumentShareMapper.toResponseList(shares)
        );
    }

    // ---------------------------------------------------------
    // Check If Document Is Shared With User
    // ---------------------------------------------------------

    @GetMapping("/document/{documentId}/user/{userId}/exists")
    public ResponseEntity<Boolean> isSharedWith(
            @PathVariable UUID documentId,
            @PathVariable UUID userId
    ) {

        boolean shared =
                documentShareService.isSharedWith(
                        documentId,
                        userId
                );

        return ResponseEntity.ok(shared);
    }

    // ---------------------------------------------------------
    // Revoke Share
    // ---------------------------------------------------------

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revokeShare(
            @PathVariable UUID shareId
    ) {

        documentShareService.revokeShare(shareId);

        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------
    // Revoke Share From User
    // ---------------------------------------------------------

    @DeleteMapping("/document/{documentId}/user/{sharedWith}")
    public ResponseEntity<Void> revokeShareFromUser(
            @PathVariable UUID documentId,
            @PathVariable UUID sharedWith
    ) {

        documentShareService.revokeShareFromUser(
                documentId,
                sharedWith
        );

        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------
    // Revoke All Shares For Document
    // ---------------------------------------------------------

    @DeleteMapping("/document/{documentId}")
    public ResponseEntity<Void> revokeAllShares(
            @PathVariable UUID documentId
    ) {

        documentShareService.revokeAllShares(documentId);

        return ResponseEntity.noContent().build();
    }
}