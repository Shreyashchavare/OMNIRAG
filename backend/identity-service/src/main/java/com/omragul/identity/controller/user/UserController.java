package com.omragul.identity.controller.user;

import com.omragul.identity.dto.request.user.CreateUserRequestDto;
import com.omragul.identity.dto.request.user.UpdateUserProfileRequestDto;
import com.omragul.identity.dto.request.user.UpdateUserRequestDto;
import com.omragul.identity.dto.response.user.UserProfileResponseDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.service.user.UserProfileService;
import com.omragul.identity.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileService userProfileService;

    @PreAuthorize("hasAuthority('USER_CREATE')")
    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(
            @Valid @RequestBody CreateUserRequestDto request,
            Authentication authentication
    ) {

        UUID adminUserId =
                (UUID) authentication.getPrincipal();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        userService.createUserByAdmin(
                                request,
                                adminUserId
                        )
                );
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUser(
            @PathVariable UUID userId
    ) {

        return ResponseEntity.ok(
                userService.getUserById(userId)
        );
    }

    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequestDto request
    ) {

        return ResponseEntity.ok(
                userService.updateUser(userId, request)
        );
    }

    @PreAuthorize("hasAuthority('USER_DELETE')")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId
    ) {

        userService.deleteUser(userId);

        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('USER_RESTORE')")
    @PatchMapping("/{userId}/restore")
    public ResponseEntity<Void> restoreUser(
            @PathVariable UUID userId
    ) {

        userService.restoreUser(userId);

        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('USER_LOCK')")
    @PatchMapping("/{userId}/lock")
    public ResponseEntity<Void> lockUser(
            @PathVariable UUID userId
    ) {

        userService.lockUser(userId);

        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('USER_UNLOCK')")
    @PatchMapping("/{userId}/unlock")
    public ResponseEntity<Void> unlockUser(
            @PathVariable UUID userId
    ) {

        userService.unlockUser(userId);

        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('USER_PROFILE_READ')")
    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponseDto> getProfile(
            @PathVariable UUID userId
    ) {

        return ResponseEntity.ok(
                userProfileService.getProfile(userId)
        );
    }

    @PreAuthorize("hasAuthority('USER_PROFILE_UPDATE')")
    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponseDto> updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserProfileRequestDto request
    ) {

        return ResponseEntity.ok(
                userProfileService.updateProfile(userId, request)
        );
    }
}