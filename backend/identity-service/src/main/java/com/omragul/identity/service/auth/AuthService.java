package com.omragul.identity.service.auth;

import com.omragul.identity.dto.request.auth.LoginRequestDto;
import com.omragul.identity.dto.request.auth.SignupRequestDto;
import com.omragul.identity.dto.response.auth.LoginResponseDto;
import com.omragul.identity.dto.response.auth.SignupResponseDto;
import com.omragul.identity.dto.response.auth.TokenResponseDto;

public interface AuthService {

    SignupResponseDto signup(SignupRequestDto request);

    LoginResponseDto login(LoginRequestDto request);

    TokenResponseDto refreshAccessToken(String refreshToken);

    void logout(String refreshToken);
}