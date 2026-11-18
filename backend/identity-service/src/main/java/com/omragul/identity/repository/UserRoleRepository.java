package com.omragul.identity.repository;

import com.omragul.identity.entity.rbac.UserRole;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends CrudRepository<UserRole, UUID> {

    List<UserRole> findByUserUserId(UUID userId);

    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role WHERE ur.user.userId = :userId")
    List<UserRole> findByUserUserIdWithRole(@Param("userId") UUID userId);

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