package com.omragul.identity.repository;

import com.omragul.identity.entity.rbac.Role;
import com.omragul.identity.enums.RoleType;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends CrudRepository<Role, UUID> {

    Optional<Role> findByRoleName(RoleType roleName);

    boolean existsByRoleName(RoleType roleName);

    List<Role> findAllByOrderByRoleNameAsc();
}
