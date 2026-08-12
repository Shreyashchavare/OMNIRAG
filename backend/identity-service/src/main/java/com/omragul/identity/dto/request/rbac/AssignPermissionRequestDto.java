package com.omragul.identity.dto.request.rbac;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignPermissionRequestDto {

    @NotNull(message = "Permission ID is required")
    private UUID permissionId;

    private Instant expiresAt;
}