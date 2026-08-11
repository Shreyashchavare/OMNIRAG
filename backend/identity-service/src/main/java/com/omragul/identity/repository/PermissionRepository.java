package com.omragul.identity.repository;

import com.omragul.identity.entity.rbac.Permission;
import com.omragul.identity.enums.PermissionType;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends CrudRepository<Permission, UUID> {

    Optional<Permission> findByPermissionName(PermissionType permissionName);

    boolean existsByPermissionName(PermissionType permissionName);

    List<Permission> findAllByOrderByPermissionNameAsc();
}