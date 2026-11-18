package com.omragul.identity.service.security;

import com.omragul.identity.entity.rbac.Permission;
import com.omragul.identity.entity.rbac.Role;
import com.omragul.identity.entity.rbac.RolePermission;
import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.enums.PermissionType;
import com.omragul.identity.enums.RoleType;
import com.omragul.identity.repository.RolePermissionRepository;
import com.omragul.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacAuthorizationServiceImplTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private RbacAuthorizationServiceImpl rbacAuthorizationService;

    private UUID userId;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getUserPermissions: returns distinct permissions via batch query")
    void shouldReturnUserPermissionsViaBatchQuery() {
        Role role = Role.builder().roleId(roleId).roleName(RoleType.USER).build();
        UserRole userRole = UserRole.builder().role(role).build();

        Permission perm1 = Permission.builder().permissionName(PermissionType.USER_PROFILE_READ).build();
        Permission perm2 = Permission.builder().permissionName(PermissionType.RAG_QUERY).build();

        RolePermission rp1 = RolePermission.builder().role(role).permission(perm1).build();
        RolePermission rp2 = RolePermission.builder().role(role).permission(perm2).build();

        when(userRoleRepository.findByUserUserIdWithRole(userId)).thenReturn(List.of(userRole));
        when(rolePermissionRepository.findByRoleRoleIdInWithPermissions(List.of(roleId))).thenReturn(List.of(rp1, rp2));

        Set<String> permissions = rbacAuthorizationService.getUserPermissions(userId);

        assertThat(permissions).containsExactlyInAnyOrder("USER_PROFILE_READ", "RAG_QUERY");
        verify(rolePermissionRepository, times(1)).findByRoleRoleIdInWithPermissions(anyCollection());
    }

    @Test
    @DisplayName("getUserPermissions: returns empty set immediately when user has no roles")
    void shouldReturnEmptyPermissionsWhenNoRoles() {
        when(userRoleRepository.findByUserUserIdWithRole(userId)).thenReturn(Collections.emptyList());

        Set<String> permissions = rbacAuthorizationService.getUserPermissions(userId);

        assertThat(permissions).isEmpty();
        verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    @DisplayName("getUserRoles: returns role names for user")
    void shouldReturnUserRoles() {
        Role role = Role.builder().roleId(roleId).roleName(RoleType.ADMIN).build();
        UserRole userRole = UserRole.builder().role(role).build();

        when(userRoleRepository.findByUserUserIdWithRole(userId)).thenReturn(List.of(userRole));

        Set<String> roles = rbacAuthorizationService.getUserRoles(userId);

        assertThat(roles).containsExactly("ADMIN");
    }
}
