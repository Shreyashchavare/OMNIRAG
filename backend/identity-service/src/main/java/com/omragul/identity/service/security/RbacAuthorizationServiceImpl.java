package com.omragul.identity.service.security;

import com.omragul.identity.entity.rbac.RolePermission;
import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.repository.RolePermissionRepository;
import com.omragul.identity.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RbacAuthorizationServiceImpl
        implements RbacAuthorizationService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<String> getUserPermissions(UUID userId) {

        Set<String> permissions = new HashSet<>();

        // 1. Get all roles assigned to the user
        List<UserRole> userRoles =
                userRoleRepository.findByUserUserId(userId);

        // 2. Get permissions for every role
        for (UserRole userRole : userRoles) {

            UUID roleId =
                    userRole.getRole().getRoleId();

            List<RolePermission> rolePermissions =
                    rolePermissionRepository
                            .findByRoleRoleId(roleId);

            // 3. Convert PermissionType to String
            for (RolePermission rolePermission : rolePermissions) {

                permissions.add(
                        rolePermission
                                .getPermission()
                                .getPermissionName()
                                .name()
                );
            }
        }

        return permissions;
    }
}