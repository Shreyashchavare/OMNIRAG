package com.omragul.identity.repository;

import com.omragul.identity.entity.rbac.UserPermission;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPermissionRepository extends CrudRepository<UserPermission, UUID> {

    List<UserPermission> findByUserUserId(UUID userId);

    List<UserPermission> findByPermissionPermissionId(UUID permissionId);

    Optional<UserPermission> findByUserUserIdAndPermissionPermissionId(
            UUID userId,
            UUID permissionId
    );

    boolean existsByUserUserIdAndPermissionPermissionId(
            UUID userId,
            UUID permissionId
    );

    void deleteByUserUserIdAndPermissionPermissionId(
            UUID userId,
            UUID permissionId
    );
}