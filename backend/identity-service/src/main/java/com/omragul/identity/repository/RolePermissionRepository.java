package com.omragul.identity.repository;

import com.omragul.identity.entity.rbac.RolePermission;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends CrudRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleRoleId(UUID roleId);

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