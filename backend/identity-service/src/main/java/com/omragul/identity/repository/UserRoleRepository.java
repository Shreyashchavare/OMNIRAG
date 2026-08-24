package com.omragul.identity.repository;

import com.omragul.identity.entity.rbac.UserRole;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends CrudRepository<UserRole, UUID> {

    List<UserRole> findByUserUserId(UUID userId);

    List<UserRole> findByRoleRoleId(UUID roleId);

    Optional<UserRole> findByUserUserIdAndRoleRoleId(
            UUID userId,
            UUID roleId
    );

    boolean existsByUserUserIdAndRoleRoleId(
            UUID userId,
            UUID roleId
    );

    void deleteByUserUserIdAndRoleRoleId(
            UUID userId,
            UUID roleId
    );
}