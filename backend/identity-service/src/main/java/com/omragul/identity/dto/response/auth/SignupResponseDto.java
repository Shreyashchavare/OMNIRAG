package com.omragul.identity.dto.response.auth;

import com.omragul.identity.dto.response.user.UserResponseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponseDto {

    private String message;

    private UserResponseDto user;
}