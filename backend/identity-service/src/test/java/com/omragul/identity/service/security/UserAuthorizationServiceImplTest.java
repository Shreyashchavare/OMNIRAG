package com.omragul.identity.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UserAuthorizationServiceImplTest {

    @Mock
    private RbacAuthorizationService rbacAuthorizationService;

    private UserAuthorizationServiceImpl userAuthorizationService;

    @BeforeEach
    void setUp() {
        userAuthorizationService = new UserAuthorizationServiceImpl(rbacAuthorizationService);
    }

    @Test
    @DisplayName("canAccessUser returns true when user has ROLE_ADMIN authority regardless of userId")
    void canAccessUser_shouldReturnTrue_whenUserHasAdminRole() {
        UUID requestedUserId = UUID.randomUUID();
        UUID adminPrincipalId = UUID.randomUUID(); // different from requestedUserId

        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                adminPrincipalId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("USER_PROFILE_READ"))
        );

        boolean result = userAuthorizationService.canAccessUser(requestedUserId, adminAuth);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccessUser returns true when authenticated UUID matches requested userId")
    void canAccessUser_shouldReturnTrue_whenUserIsOwnerWithUUIDPrincipal() {
        UUID userId = UUID.randomUUID();

        Authentication ownerAuth = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("USER_PROFILE_READ"))
        );

        boolean result = userAuthorizationService.canAccessUser(userId, ownerAuth);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccessUser returns false when authenticated UUID differs from requested userId (BOLA defense)")
    void canAccessUser_shouldReturnFalse_whenUserIsNotOwner() {
        UUID requestedUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Authentication otherAuth = new UsernamePasswordAuthenticationToken(
                otherUserId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("USER_PROFILE_READ"))
        );

        boolean result = userAuthorizationService.canAccessUser(requestedUserId, otherAuth);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canAccessUser returns true when principal is a String matching requested UUID")
    void canAccessUser_shouldReturnTrue_whenPrincipalIsMatchingString() {
        UUID requestedUserId = UUID.randomUUID();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                requestedUserId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        boolean result = userAuthorizationService.canAccessUser(requestedUserId, auth);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccessUser returns true when principal is UserDetails with username matching requested UUID")
    void canAccessUser_shouldReturnTrue_whenPrincipalIsMatchingUserDetails() {
        UUID requestedUserId = UUID.randomUUID();
        UserDetails userDetails = new User(requestedUserId.toString(), "password", Collections.emptyList());

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        boolean result = userAuthorizationService.canAccessUser(requestedUserId, auth);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccessUser returns false when principal is an invalid non-UUID string")
    void canAccessUser_shouldReturnFalse_whenPrincipalIsInvalidString() {
        UUID requestedUserId = UUID.randomUUID();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "not-a-valid-uuid",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        boolean result = userAuthorizationService.canAccessUser(requestedUserId, auth);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canAccessUser returns false when authentication is null")
    void canAccessUser_shouldReturnFalse_whenAuthenticationIsNull() {
        UUID requestedUserId = UUID.randomUUID();

        boolean result = userAuthorizationService.canAccessUser(requestedUserId, null);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canAccessUser returns false when requestedUserId is null")
    void canAccessUser_shouldReturnFalse_whenRequestedUserIdIsNull() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        boolean result = userAuthorizationService.canAccessUser(null, auth);

        assertThat(result).isFalse();
    }
}

