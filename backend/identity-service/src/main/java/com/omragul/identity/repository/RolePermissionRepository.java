package com.omragul.identity.repository;

import com.omragul.identity.entity.rbac.RolePermission;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends CrudRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleRoleId(UUID roleId);

    @Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.permission WHERE rp.role.roleId IN :roleIds")
    List<RolePermission> findByRoleRoleIdInWithPermissions(@Param("roleIds") Collection<UUID> roleIds);

    List<RolePermission> findByPermissionPermissionId(UUID permissionId);

    Optional<RolePermission> findByRoleRoleIdAndPermissionPermissionId(
            UUID roleId,
            UUID permissionId
    );

    boolean existsByRoleRoleIdAndPermissionPermissionId(
            UUID roleId,
            UUID permissionId
    );

    void deleteByRoleRoleIdAndPermissionPermissionId(
            UUID roleId,
            UUID permissionId
    );
}