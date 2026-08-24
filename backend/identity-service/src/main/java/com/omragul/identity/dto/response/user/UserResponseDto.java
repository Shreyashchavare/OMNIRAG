package com.omragul.identity.dto.response.user;

import com.omragul.identity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    private UUID userId;

    private String username;

    private String email;

    private UserStatus status;

    private Boolean emailVerified;

    private UserProfileResponseDto profile;
}