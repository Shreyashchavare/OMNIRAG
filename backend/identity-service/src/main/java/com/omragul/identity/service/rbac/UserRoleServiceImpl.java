package com.omragul.identity.service.rbac;

import com.omragul.identity.dto.response.rbac.RoleResponseDto;
import com.omragul.identity.entity.rbac.Role;
import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.exception.IdentityException;
import com.omragul.identity.exception.ResourceNotFoundException;
import com.omragul.identity.mapper.RoleMapper;
import com.omragul.identity.repository.RoleRepository;
import com.omragul.identity.repository.UserRepository;
import com.omragul.identity.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    @Override
    @Transactional
    public void assignRole(
            UUID userId,
            UUID roleId,
            UUID assignedBy
    ) {

        // 1. Check user
        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        // 2. Check role
        Role role = roleRepository
                .findById(roleId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Role not found with id: " + roleId
                        )
                );

        // 3. Prevent duplicate role assignment
        if (userRoleRepository
                .existsByUserUserIdAndRoleRoleId(userId, roleId)) {

            throw new IdentityException(
                    "Role is already assigned to this user"
            );
        }

        // 4. Create user-role mapping
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .assignedBy(assignedBy)
                .build();

        userRoleRepository.save(userRole);
    }

    @Override
    @Transactional
    public void revokeRole(
            UUID userId,
            UUID roleId
    ) {

        // 1. Check user
        userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        // 2. Check whether role is assigned
        if (!userRoleRepository
                .existsByUserUserIdAndRoleRoleId(userId, roleId)) {

            throw new ResourceNotFoundException(
                    "Role is not assigned to this user"
            );
        }

        // 3. Remove mapping
        userRoleRepository
                .deleteByUserUserIdAndRoleRoleId(
                        userId,
                        roleId
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponseDto> getUserRoles(
            UUID userId
    ) {

        // 1. Check user
        userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        // 2. Get roles
        return userRoleRepository
                .findByUserUserId(userId)
                .stream()
                .map(UserRole::getRole)
                .map(roleMapper::toResponseDto)
                .toList();
    }
}