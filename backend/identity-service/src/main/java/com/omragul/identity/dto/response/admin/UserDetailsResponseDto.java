package com.omragul.identity.dto.response.admin;

import com.omragul.identity.dto.response.rbac.PermissionResponseDto;
import com.omragul.identity.dto.response.user.UserProfileResponseDto;
import com.omragul.identity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsResponseDto {

    private UUID userId;

    private String username;

    private String email;

    private UserStatus status;

    private Boolean emailVerified;

    private Boolean accountLocked;

    private Integer failedLoginAttempts;

    private Instant lastLoginAt;

    private Instant createdAt;

    private Instant updatedAt;

    private UserProfileResponseDto profile;

    private Set<String> roles;

    private Set<PermissionResponseDto> permissions;
}