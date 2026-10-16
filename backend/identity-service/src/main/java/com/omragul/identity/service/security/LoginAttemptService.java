package com.omragul.identity.service.security;

import com.omragul.identity.entity.user.User;

import java.util.UUID;

public interface LoginAttemptService {

    void recordFailedLoginAttempt(UUID userId);

    void recordSuccessfulLogin(UUID userId);

}