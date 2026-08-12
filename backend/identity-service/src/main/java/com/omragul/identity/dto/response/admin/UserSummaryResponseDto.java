package com.omragul.identity.dto.response.admin;

import com.omragul.identity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponseDto {

    private UUID userId;

    private String username;

    private String email;

    private UserStatus status;

    private Boolean emailVerified;
}