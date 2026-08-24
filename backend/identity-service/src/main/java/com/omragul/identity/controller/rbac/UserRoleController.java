package com.omragul.identity.controller.rbac;

import com.omragul.identity.dto.response.rbac.RoleResponseDto;
import com.omragul.identity.service.rbac.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRoleService userRoleService;

    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @PostMapping("/{userId}/roles/{roleId}")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            Authentication authentication
    ) {

        UUID assignedBy =
                (UUID) authentication.getPrincipal();

        userRoleService.assignRole(
                userId,
                roleId,
                assignedBy
        );

        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('ROLE_REVOKE')")
    @DeleteMapping("/{userId}/roles/{roleId}")
    public ResponseEntity<Void> revokeRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId
    ) {

        userRoleService.revokeRole(
                userId,
                roleId
        );

        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('ROLE_READ')")
    @GetMapping("/{userId}/roles")
    public ResponseEntity<List<RoleResponseDto>> getUserRoles(
            @PathVariable UUID userId
    ) {

        return ResponseEntity.ok(
                userRoleService.getUserRoles(userId)
        );
    }
}