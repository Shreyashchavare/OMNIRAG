package com.omragul.identity.service.user;

import com.omragul.identity.dto.request.user.UpdateUserProfileRequestDto;
import com.omragul.identity.dto.response.user.UserProfileResponseDto;

import java.util.UUID;

public interface UserProfileService {

    UserProfileResponseDto getProfile(UUID userId);

    UserProfileResponseDto updateProfile(
            UUID userId,
            UpdateUserProfileRequestDto request
    );
}