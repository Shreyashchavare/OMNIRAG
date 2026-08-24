package com.omragul.identity.service.user;

import com.omragul.identity.dto.request.user.CreateUserRequestDto;
import com.omragul.identity.dto.request.user.SignupProfileRequestDto;
import com.omragul.identity.dto.request.user.UpdateUserRequestDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.entity.rbac.Role;
import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.entity.user.UserProfile;
import com.omragul.identity.enums.RoleType;
import com.omragul.identity.enums.UserStatus;
import com.omragul.identity.exception.IdentityException;
import com.omragul.identity.exception.ResourceNotFoundException;
import com.omragul.identity.exception.UserAlreadyExistsException;
import com.omragul.identity.mapper.UserMapper;
import com.omragul.identity.mapper.UserProfileMapper;
import com.omragul.identity.repository.RoleRepository;
import com.omragul.identity.repository.UserRepository;
import com.omragul.identity.repository.UserRoleRepository;
import com.omragul.identity.service.security.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    private final PasswordService passwordService;

    @Value("${identity.security.max-failed-login-attempts}")
    private int maxFailedLoginAttempts;

    @Override
    @Transactional
    public User createUser(
            String username,
            String email,
            String passwordHash,
            SignupProfileRequestDto profile
    ) {

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .build();

        UserProfile userProfile =
                userProfileMapper.toEntity(profile);

        userProfile.setUser(user);

        user.setUserProfile(userProfile);

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponseDto createUserByAdmin(
            CreateUserRequestDto request,
            UUID assignedBy
    ) {

        // 1. Check username
        if (existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(
                    "Username already exists: " + request.getUsername()
            );
        }

        // 2. Check email
        if (existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "Email already exists: " + request.getEmail()
            );
        }

        // 3. Hash password
        String passwordHash =
                passwordService.hashPassword(request.getPassword());

        // 4. Create user + profile
        User user = createUser(
                request.getUsername(),
                request.getEmail(),
                passwordHash,
                request.getProfile()
        );

        // 5. Find default USER role
        Role userRole = roleRepository
                .findByRoleName(RoleType.USER)
                .orElseThrow(() ->
                        new IdentityException(
                                "Default USER role not found"
                        )
                );

        // 6. Assign USER role
        UserRole userRoleMapping = UserRole.builder()
                .user(user)
                .role(userRole)
                .assignedBy(assignedBy)
                .build();

        userRoleRepository.save(userRoleMapping);

        // 7. Return response
        return userMapper.toResponseDto(user);
    }

    @Override
    public UserResponseDto getUserById(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        return userMapper.toResponseDto(user);
    }

    @Override
    public UserResponseDto getUserByUsername(String username) {

        User user = userRepository
                .findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with username: " + username
                        )
                );

        return userMapper.toResponseDto(user);
    }

    @Override
    public UserResponseDto getUserByEmail(String email) {

        User user = userRepository
                .findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with email: " + email
                        )
                );

        return userMapper.toResponseDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserEntityByUsername(String username) {

        return userRepository
                .findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with username: " + username
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserEntityByEmail(String email) {

        return userRepository
                .findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with email: " + email
                        )
                );
    }

    @Override
    @Transactional
    public UserResponseDto updateUser(
            UUID userId,
            UpdateUserRequestDto request
    ) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );
        userMapper.updateUser(request, user);

        User updatedUser = userRepository.save(user);

        return userMapper.toResponseDto(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {

        return userRepository
                .existsByUsernameAndIsDeletedFalse(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {

        return userRepository
                .existsByEmailAndIsDeletedFalse(email);
    }

//    @Override
//    @Transactional
//    public void recordFailedLoginAttempt(UUID userId) {
//
//        User user = userRepository
//                .findByUserIdAndIsDeletedFalse(userId)
//                .orElseThrow(() ->
//                        new ResourceNotFoundException(
//                                "User not found with id: " + userId
//                        )
//                );
//
//        int failedAttempts = user.getFailedLoginAttempts() + 1;
//
//        user.setFailedLoginAttempts(failedAttempts);
//
//        if (failedAttempts >= maxFailedLoginAttempts) {
//            user.setAccountLocked(true);
//            user.setStatus(UserStatus.LOCKED);
//        }
//
//        userRepository.save(user);
//    }
//
//    @Override
//    @Transactional
//    public void recordSuccessfulLogin(UUID userId) {
//
//        User user = userRepository
//                .findByUserIdAndIsDeletedFalse(userId)
//                .orElseThrow(() ->
//                        new ResourceNotFoundException(
//                                "User not found with id: " + userId
//                        )
//                );
//
//        user.setFailedLoginAttempts(0);
//        user.setLastLoginAt(Instant.now());
//
//        userRepository.save(user);
//    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        user.setIsDeleted(true);
        user.setDeletedAt(Instant.now());

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void restoreUser(UUID userId) {
        User user = userRepository
                .findByUserIdAndIsDeletedTrue(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Deleted user not found with id: " + userId
                        )
                );

        user.setIsDeleted(false);
        user.setDeletedAt(null);
        user.setDeletedBy(null);

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void lockUser(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        user.setAccountLocked(true);
        user.setStatus(UserStatus.LOCKED);

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void unlockUser(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.setStatus(UserStatus.ACTIVE);

        userRepository.save(user);
    }
}