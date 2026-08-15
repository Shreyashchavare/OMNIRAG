package com.omragul.identity.service.user;

import com.omragul.identity.dto.request.user.UpdateUserRequestDto;
import com.omragul.identity.dto.response.user.UserResponseDto;

import java.util.UUID;

public interface UserService {

    UserResponseDto getUserById(UUID userId);

    UserResponseDto getUserByUsername(String username);

    UserResponseDto getUserByEmail(String email);

    UserResponseDto updateUser(
            UUID userId,
            UpdateUserRequestDto request
    );

    void deleteUser(UUID userId);

    void restoreUser(UUID userId);

    void lockUser(UUID userId);

    void unlockUser(UUID userId);
}