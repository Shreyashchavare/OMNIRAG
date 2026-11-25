package com.omragul.processing.security;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class UserSecurityContext {
    private final UUID userId;
    private final Set<String> roles;
    private final boolean internalService;
    private final boolean admin;

    public static UserSecurityContext internal() {
        return UserSecurityContext.builder()
                .userId(null)
                .roles(Set.of("ROLE_INTERNAL_SERVICE"))
                .internalService(true)
                .admin(false)
                .build();
    }

    public static UserSecurityContext user(UUID userId, Set<String> roles) {
        boolean isAdmin = roles != null && roles.stream().anyMatch(r ->
                r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN")
        );
        return UserSecurityContext.builder()
                .userId(userId)
                .roles(roles != null ? Collections.unmodifiableSet(roles) : Collections.emptySet())
                .internalService(false)
                .admin(isAdmin)
                .build();
    }
}
