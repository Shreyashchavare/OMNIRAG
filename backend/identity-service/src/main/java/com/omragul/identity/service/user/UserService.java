package com.omragul.identity.service.user;

import com.omragul.identity.dto.request.user.CreateUserRequestDto;
import com.omragul.identity.dto.request.user.SignupProfileRequestDto;
import com.omragul.identity.dto.request.user.UpdateUserRequestDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.entity.user.User;

import java.util.UUID;

public interface UserService {

    User createUser(
            String username,
            String email,
            String passwordHash,
            SignupProfileRequestDto profile
    );

//    UserResponseDto createUser(
//            String username,
//            String email,
//            String passwordHash,
//            SignupProfileRequestDto profile
//    );

    UserResponseDto createUserByAdmin(
        CreateUserRequestDto request,
        UUID assignedBy
    );

    User getUserEntityByUsername(String username);

    User getUserEntityByEmail(String email);

    UserResponseDto getUserById(UUID userId);

    UserResponseDto getUserByUsername(String username);

    UserResponseDto getUserByEmail(String email);

    UserResponseDto updateUser(
            UUID userId,
            UpdateUserRequestDto request
    );

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

//    void recordFailedLoginAttempt(UUID userId);
//
//    void recordSuccessfulLogin(UUID userId);

    void deleteUser(UUID userId);

    void restoreUser(UUID userId);

    void lockUser(UUID userId);

    void unlockUser(UUID userId);
}