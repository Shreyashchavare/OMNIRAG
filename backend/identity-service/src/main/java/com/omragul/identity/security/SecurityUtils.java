package com.omragul.identity.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Safely extracts the user UUID from Authentication principal.
     * Supports UUID, String containing UUID, and UserDetails where username is UUID
     * string.
     */
    public static UUID extractUserId(Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UUID uuid) {
            return uuid;
        } else if (principal instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedException("Invalid principal UUID");
            }
        } else if (principal instanceof UserDetails userDetails) {
            try {
                return UUID.fromString(userDetails.getUsername());
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedException("Invalid user principal UUID");
            }
        }

        throw new AccessDeniedException(
                "Unsupported principal type: " + (principal != null ? principal.getClass().getName() : "null"));
    }
}
