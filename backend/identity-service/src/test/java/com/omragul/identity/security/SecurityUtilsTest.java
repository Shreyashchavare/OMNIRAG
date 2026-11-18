package com.omragul.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @Test
    @DisplayName("extractUserId: successfully extracts UUID from UUID principal")
    void shouldExtractUserIdFromUUID() {
        UUID expectedId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(expectedId, null, Collections.emptyList());

        UUID result = SecurityUtils.extractUserId(auth);

        assertThat(result).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("extractUserId: successfully extracts UUID from String principal containing valid UUID")
    void shouldExtractUserIdFromString() {
        UUID expectedId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(expectedId.toString(), null,
                Collections.emptyList());

        UUID result = SecurityUtils.extractUserId(auth);

        assertThat(result).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("extractUserId: successfully extracts UUID from UserDetails principal where username is UUID string")
    void shouldExtractUserIdFromUserDetails() {
        UUID expectedId = UUID.randomUUID();
        UserDetails userDetails = User.withUsername(expectedId.toString())
                .authorities(Collections.emptyList())
                .build();
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());

        UUID result = SecurityUtils.extractUserId(auth);

        assertThat(result).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("extractUserId: throws AccessDeniedException when authentication is null")
    void shouldThrowWhenAuthNull() {
        assertThatThrownBy(() -> SecurityUtils.extractUserId(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("extractUserId: throws AccessDeniedException when principal String is not a valid UUID")
    void shouldThrowWhenPrincipalStringInvalidUUID() {
        Authentication auth = new UsernamePasswordAuthenticationToken("not-a-uuid", null, Collections.emptyList());

        assertThatThrownBy(() -> SecurityUtils.extractUserId(auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Invalid principal UUID");
    }

    @Test
    @DisplayName("extractUserId: throws AccessDeniedException when principal type is unsupported")
    void shouldThrowWhenPrincipalTypeUnsupported() {
        Authentication auth = new UsernamePasswordAuthenticationToken(12345, null, Collections.emptyList());

        assertThatThrownBy(() -> SecurityUtils.extractUserId(auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unsupported principal type");
    }
}
