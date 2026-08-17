package com.omragul.identity.service.auth;

import com.omragul.identity.dto.request.auth.LoginRequestDto;
import com.omragul.identity.dto.request.auth.SignupRequestDto;
import com.omragul.identity.dto.response.auth.LoginResponseDto;
import com.omragul.identity.dto.response.auth.SignupResponseDto;

public interface AuthService {

    SignupResponseDto signup(SignupRequestDto request);

    LoginResponseDto login(LoginRequestDto request);
}