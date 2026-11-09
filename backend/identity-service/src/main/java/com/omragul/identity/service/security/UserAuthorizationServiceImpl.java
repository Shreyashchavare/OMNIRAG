package com.omragul.identity.service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("userAuthorizationService")
@RequiredArgsConstructor
public class UserAuthorizationServiceImpl implements UserAuthorizationService {

    @SuppressWarnings("unused")
    private final RbacAuthorizationService rbacAuthorizationService;

    @Override
    public boolean canAccessUser(
            UUID requestedUserId,
            Authentication authentication
    ) {
        if (authentication == null || requestedUserId == null) {
            return false;
        }

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
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID authenticatedUserId) {
            return authenticatedUserId.equals(requestedUserId);
        } else if (principal instanceof String principalString) {
            try {
                return UUID.fromString(principalString).equals(requestedUserId);
            } catch (IllegalArgumentException e) {
                return false;
            }
        } else if (principal instanceof UserDetails userDetails) {
            try {
                return UUID.fromString(userDetails.getUsername()).equals(requestedUserId);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        return false;
    }
}
