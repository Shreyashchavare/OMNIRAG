package com.omragul.identity.service.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public interface UserAuthorizationService {

    public boolean canAccessUser(
            UUID requestedUserId,
            Authentication authentication
    );
}
