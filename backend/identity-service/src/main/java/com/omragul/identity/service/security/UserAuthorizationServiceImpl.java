package com.omragul.identity.service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAuthorizationServiceImpl implements UserAuthorizationService{

    private final RbacAuthorizationService rbacAuthorizationService;

    public boolean canAccessUser(
            UUID requestedUserId,
            Authentication authentication
    ) {

        // ADMIN can access any user's resource
        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN")
                );

        if (isAdmin) {
            return true;
        }

        // USER can access only their own resource
        UUID authenticatedUserId =
                (UUID) authentication.getPrincipal();

        return authenticatedUserId.equals(requestedUserId);
    }
}
