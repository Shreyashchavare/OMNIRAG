package com.omragul.identity.dto.response.rbac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponseDto {

    private UUID permissionId;

    private String permissionName;

    private String description;

    private String resourceType;

    private String action;
}