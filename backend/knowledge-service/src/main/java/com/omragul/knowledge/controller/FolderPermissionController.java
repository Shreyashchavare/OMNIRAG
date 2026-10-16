package com.omragul.knowledge.controller;

import com.omragul.knowledge.dto.FolderPermissionRequest;
import com.omragul.knowledge.dto.FolderPermissionResponse;
import com.omragul.knowledge.entity.FolderPermission;
import com.omragul.knowledge.mapper.FolderPermissionMapper;
import com.omragul.knowledge.service.FolderPermissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folder-permissions")
@Tag(
        name = "Folder Permission",
        description = "APIs for folder's permissions"
)
public class FolderPermissionController {

    private final FolderPermissionService folderPermissionService;

    public FolderPermissionController(
            FolderPermissionService folderPermissionService
    ) {
        this.folderPermissionService = folderPermissionService;
    }

    // ---------------------------------------------------------
    // Grant Permission
    // ---------------------------------------------------------

    @PostMapping
    public ResponseEntity<FolderPermissionResponse> grantPermission(
            @Valid @RequestBody FolderPermissionRequest request
    ) {

        FolderPermission permission =
                folderPermissionService.grantPermission(
                        request.getFolderId(),
                        request.getPrincipalType(),
                        request.getPrincipalId(),
                        request.getPermission(),
                        null,
                        request.getExpiresAt()
                );

        return ResponseEntity.ok(
                FolderPermissionMapper.toResponse(permission)
        );
    }

    // ---------------------------------------------------------
    // Get Permission By ID
    // ---------------------------------------------------------

    @GetMapping("/{permissionId}")
    public ResponseEntity<FolderPermissionResponse> getById(
            @PathVariable UUID permissionId
    ) {

        FolderPermission permission =
                folderPermissionService.getById(permissionId);

        return ResponseEntity.ok(
                FolderPermissionMapper.toResponse(permission)
        );
    }

    // ---------------------------------------------------------
    // Get All Permissions For Folder
    // ---------------------------------------------------------

    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<FolderPermissionResponse>> getByFolderId(
            @PathVariable UUID folderId
    ) {

        List<FolderPermission> permissions =
                folderPermissionService.getByFolderId(folderId);

        return ResponseEntity.ok(
                FolderPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Get Permissions By Principal
    // ---------------------------------------------------------

    @GetMapping("/principal/{principalId}")
    public ResponseEntity<List<FolderPermissionResponse>> getByPrincipalId(
            @PathVariable UUID principalId
    ) {

        List<FolderPermission> permissions =
                folderPermissionService.getByPrincipalId(principalId);

        return ResponseEntity.ok(
                FolderPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Get Permissions For Folder + Principal
    // ---------------------------------------------------------

    @GetMapping("/folder/{folderId}/principal/{principalId}")
    public ResponseEntity<List<FolderPermissionResponse>>
    getByFolderAndPrincipal(
            @PathVariable UUID folderId,
            @PathVariable UUID principalId
    ) {

        List<FolderPermission> permissions =
                folderPermissionService.getByFolderAndPrincipal(
                        folderId,
                        principalId
                );

        return ResponseEntity.ok(
                FolderPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Get Permissions By Principal Type
    // ---------------------------------------------------------

    @GetMapping("/folder/{folderId}/type/{principalType}")
    public ResponseEntity<List<FolderPermissionResponse>>
    getByPrincipalType(
            @PathVariable UUID folderId,
            @PathVariable FolderPermission.PrincipalType principalType
    ) {

        List<FolderPermission> permissions =
                folderPermissionService.getByPrincipalType(
                        folderId,
                        principalType
                );

        return ResponseEntity.ok(
                FolderPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Check Permission
    // ---------------------------------------------------------

    @GetMapping(
            "/folder/{folderId}/principal/{principalId}/permission/{permission}"
    )
    public ResponseEntity<Boolean> hasPermission(
            @PathVariable UUID folderId,
            @PathVariable UUID principalId,
            @PathVariable FolderPermission.Permission permission
    ) {

        boolean hasPermission =
                folderPermissionService.hasPermission(
                        folderId,
                        principalId,
                        permission
                );

        return ResponseEntity.ok(hasPermission);
    }

    // ---------------------------------------------------------
    // Get Active Permissions
    // ---------------------------------------------------------

    @GetMapping("/folder/{folderId}/active")
    public ResponseEntity<List<FolderPermissionResponse>>
    getActivePermissions(
            @PathVariable UUID folderId
    ) {

        List<FolderPermission> permissions =
                folderPermissionService.getActivePermissions(folderId);

        return ResponseEntity.ok(
                FolderPermissionMapper.toResponseList(permissions)
        );
    }

    // ---------------------------------------------------------
    // Revoke Permission
    // ---------------------------------------------------------

    @DeleteMapping("/{permissionId}")
    public ResponseEntity<Void> revokePermission(
            @PathVariable UUID permissionId
    ) {

        folderPermissionService.revokePermission(permissionId);

        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------
    // Revoke All Permissions For Principal
    // ---------------------------------------------------------

    @DeleteMapping("/folder/{folderId}/principal/{principalId}")
    public ResponseEntity<Void> revokePermissionsForPrincipal(
            @PathVariable UUID folderId,
            @PathVariable UUID principalId
    ) {

        folderPermissionService.revokePermissionsForPrincipal(
                folderId,
                principalId
        );

        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------
    // Revoke All Permissions For Folder
    // ---------------------------------------------------------

    @DeleteMapping("/folder/{folderId}")
    public ResponseEntity<Void> revokeAllPermissions(
            @PathVariable UUID folderId
    ) {

        folderPermissionService.revokeAllPermissions(folderId);

        return ResponseEntity.noContent().build();
    }
}