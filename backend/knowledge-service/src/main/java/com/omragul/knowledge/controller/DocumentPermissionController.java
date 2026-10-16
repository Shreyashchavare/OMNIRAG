package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.DocumentPermissionRequest;
import com.omragul.knowledge.dto.DocumentPermissionResponse;
import com.omragul.knowledge.entity.DocumentPermission;
import com.omragul.knowledge.mapper.DocumentPermissionMapper;
import com.omragul.knowledge.service.DocumentPermissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-permissions")
@Tag(
        name = "Document Permission",
        description = "APIs for permissions of documents"
)
public class DocumentPermissionController {

    private final DocumentPermissionService documentPermissionService;

    public DocumentPermissionController(
            DocumentPermissionService documentPermissionService
    ) {
        this.documentPermissionService = documentPermissionService;
    }

    // ---------------------------------------------------------
    // Grant Permission
    // ---------------------------------------------------------

    @PostMapping
    public ResponseEntity<DocumentPermissionResponse> grantPermission(
            @Valid @RequestBody DocumentPermissionRequest request
    ) {

        DocumentPermission permission =
                documentPermissionService.grantPermission(
                        request.getDocumentId(),
                        request.getPrincipalType(),
                        request.getPrincipalId(),
                        request.getPermission(),
                        null,
                        request.getExpiresAt()
                );

        return ResponseEntity.ok(
                DocumentPermissionMapper.toResponse(permission)
        );
    }

    // ---------------------------------------------------------
    // Get Permission By ID
    // ---------------------------------------------------------

    @GetMapping("/{permissionId}")
    public ResponseEntity<DocumentPermissionResponse> getById(
            @PathVariable UUID permissionId
    ) {

        DocumentPermission permission =
                documentPermissionService.getById(permissionId);

        return ResponseEntity.ok(
                DocumentPermissionMapper.toResponse(permission)
        );
    }

    // ---------------------------------------------------------
    // Get All Permissions For Document
    // ---------------------------------------------------------

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<DocumentPermissionResponse>> getByDocumentId(
            @PathVariable UUID documentId
    ) {

        List<DocumentPermission> permissions =
                documentPermissionService.getByDocumentId(documentId);

        return ResponseEntity.ok(
                DocumentPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Get Permissions By Principal
    // ---------------------------------------------------------

    @GetMapping("/principal/{principalId}")
    public ResponseEntity<List<DocumentPermissionResponse>> getByPrincipalId(
            @PathVariable UUID principalId
    ) {

        List<DocumentPermission> permissions =
                documentPermissionService.getByPrincipalId(principalId);

        return ResponseEntity.ok(
                DocumentPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Get Permissions For Document + Principal
    // ---------------------------------------------------------

    @GetMapping("/document/{documentId}/principal/{principalId}")
    public ResponseEntity<List<DocumentPermissionResponse>>
    getByDocumentAndPrincipal(
            @PathVariable UUID documentId,
            @PathVariable UUID principalId
    ) {

        List<DocumentPermission> permissions =
                documentPermissionService.getByDocumentAndPrincipal(
                        documentId,
                        principalId
                );

        return ResponseEntity.ok(
                DocumentPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Get Permissions By Principal Type
    // ---------------------------------------------------------

    @GetMapping("/document/{documentId}/type/{principalType}")
    public ResponseEntity<List<DocumentPermissionResponse>>
    getByPrincipalType(
            @PathVariable UUID documentId,
            @PathVariable DocumentPermission.PrincipalType principalType
    ) {

        List<DocumentPermission> permissions =
                documentPermissionService.getByPrincipalType(
                        documentId,
                        principalType
                );

        return ResponseEntity.ok(
                DocumentPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Check Permission
    // ---------------------------------------------------------

    @GetMapping(
            "/document/{documentId}/principal/{principalId}/permission/{permission}"
    )
    public ResponseEntity<Boolean> hasPermission(
            @PathVariable UUID documentId,
            @PathVariable UUID principalId,
            @PathVariable DocumentPermission.Permission permission
    ) {

        boolean hasPermission =
                documentPermissionService.hasPermission(
                        documentId,
                        principalId,
                        permission
                );

        return ResponseEntity.ok(hasPermission);
    }

    // ---------------------------------------------------------
    // Get Active Permissions
    // ---------------------------------------------------------

    @GetMapping("/document/{documentId}/active")
    public ResponseEntity<List<DocumentPermissionResponse>>
    getActivePermissions(
            @PathVariable UUID documentId
    ) {

        List<DocumentPermission> permissions =
                documentPermissionService.getActivePermissions(documentId);

        return ResponseEntity.ok(
                DocumentPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Revoke Permission
    // ---------------------------------------------------------

    @DeleteMapping("/{permissionId}")
    public ResponseEntity<Void> revokePermission(
            @PathVariable UUID permissionId
    ) {

        documentPermissionService.revokePermission(permissionId);

        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------
    // Revoke All Permissions For Principal
    // ---------------------------------------------------------

    @DeleteMapping("/document/{documentId}/principal/{principalId}")
    public ResponseEntity<Void> revokePermissionsForPrincipal(
            @PathVariable UUID documentId,
            @PathVariable UUID principalId
    ) {

        documentPermissionService.revokePermissionsForPrincipal(
                documentId,
                principalId
        );

        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------
    // Revoke All Permissions For Document
    // ---------------------------------------------------------

    @DeleteMapping("/document/{documentId}")
    public ResponseEntity<Void> revokeAllPermissions(
            @PathVariable UUID documentId
    ) {

        documentPermissionService.revokeAllPermissions(documentId);

        return ResponseEntity.noContent().build();
    }
}