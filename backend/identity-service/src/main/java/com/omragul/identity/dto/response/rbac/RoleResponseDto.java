package com.omragul.identity.dto.response.rbac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.UUID;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponseDto {

    private UUID roleId;

    private String roleName;

    private String description;

    private Set<PermissionResponseDto> permissions = new HashSet<>();
}