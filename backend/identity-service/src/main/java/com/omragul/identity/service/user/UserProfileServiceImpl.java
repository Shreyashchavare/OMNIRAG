package com.omragul.identity.service.user;

import com.omragul.identity.dto.request.user.UpdateUserProfileRequestDto;
import com.omragul.identity.dto.response.user.UserProfileResponseDto;
import com.omragul.identity.entity.user.UserProfile;
import com.omragul.identity.exception.ResourceNotFoundException;
import com.omragul.identity.mapper.UserProfileMapper;
import com.omragul.identity.repository.UserProfileRepository;
import com.omragul.identity.service.security.UserAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;
    private final UserAuthorizationService userAuthorizationService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponseDto getProfile(UUID userId) {

        // Object-level-Authorization
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!userAuthorizationService.canAccessUser(
                userId,
                authentication
        )) {
            throw new AccessDeniedException(
                    "You are not allowed to access this profile"
            );
        }

        UserProfile userProfile = userProfileRepository
                .findByUserUserId(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User profile not found for user id: " + userId
                        )
                );

        return userProfileMapper.toResponseDto(userProfile);
    }

    @Override
    @Transactional
    public UserProfileResponseDto updateProfile(
            UUID userId,
            UpdateUserProfileRequestDto request
    ) {

        // Object-level-Authorization
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!userAuthorizationService.canAccessUser(
                userId,
                authentication
        )) {
            throw new AccessDeniedException(
                    "You are not allowed to access this profile"
            );
        }

        UserProfile userProfile = userProfileRepository
                .findByUserUserId(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User profile not found for user id: " + userId
                        )
                );

        userProfileMapper.updateProfile(request, userProfile);

        UserProfile updatedUserProfile = userProfileRepository.save(userProfile);

        return userProfileMapper.toResponseDto(updatedUserProfile);
    }
}