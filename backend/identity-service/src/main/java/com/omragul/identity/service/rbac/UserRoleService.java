package com.omragul.identity.service.rbac;

import com.omragul.identity.dto.response.rbac.RoleResponseDto;

import java.util.List;
import java.util.UUID;

public interface UserRoleService {

    void assignRole(
            UUID userId,
            UUID roleId,
            UUID assignedBy
    );

    void revokeRole(
            UUID userId,
            UUID roleId
    );

    List<RoleResponseDto> getUserRoles(UUID userId);
}