package com.omragul.identity.service.security;

import com.omragul.identity.entity.rbac.RolePermission;
import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.repository.RolePermissionRepository;
import com.omragul.identity.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RbacAuthorizationServiceImpl
        implements RbacAuthorizationService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<String> getUserPermissions(UUID userId) {

        if (userId == null) {
            return Collections.emptySet();
        }

        // 1. Get all roles assigned to the user with eager fetch of role
        List<UserRole> userRoles =
                userRoleRepository.findByUserUserIdWithRole(userId);

        if (userRoles.isEmpty()) {
            return Collections.emptySet();
        }

        List<UUID> roleIds = userRoles.stream()
                .map(ur -> ur.getRole().getRoleId())
                .distinct()
                .toList();

        // 2. Fetch all role permissions in a single batch query with joined permission entity
        List<RolePermission> rolePermissions =
                rolePermissionRepository.findByRoleRoleIdInWithPermissions(roleIds);

        // 3. Convert PermissionType to String
        Set<String> permissions = new HashSet<>();
        for (RolePermission rolePermission : rolePermissions) {
            if (rolePermission.getPermission() != null
                    && rolePermission.getPermission().getPermissionName() != null) {
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

    @Override
    @Transactional(readOnly = true)
    public Set<String> getUserRoles(UUID userId) {

        if (userId == null) {
            return Collections.emptySet();
        }

        return userRoleRepository
                .findByUserUserIdWithRole(userId)
                .stream()
                .map(userRole ->
                        userRole.getRole()
                                .getRoleName()
                                .name()
                )
                .collect(Collectors.toSet());
    }
}