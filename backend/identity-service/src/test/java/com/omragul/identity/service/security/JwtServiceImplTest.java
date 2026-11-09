package com.omragul.identity.service.security;

import com.omragul.identity.entity.rbac.Role;
import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.enums.RoleType;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceImplTest {

    private JwtServiceImpl jwtService;
    private User testUser;
    private UUID testUserId;
    private Resource privateKeyResource;
    private Resource publicKeyResource;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();

        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        privateKeyResource = new ByteArrayResource(privateKeyBase64.getBytes(StandardCharsets.UTF_8));
        publicKeyResource = new ByteArrayResource(publicKeyBase64.getBytes(StandardCharsets.UTF_8));

        jwtService = new JwtServiceImpl(
                privateKeyResource,
                publicKeyResource,
                15,
                "http://localhost:8080"
        );

        testUserId = UUID.randomUUID();

        Role userRole = Role.builder()
                .roleId(UUID.randomUUID())
                .roleName(RoleType.USER)
                .build();

        UserRole userRoleMapping = UserRole.builder()
                .role(userRole)
                .build();

        testUser = User.builder()
                .userId(testUserId)
                .username("testuser")
                .email("testuser@omragul.com")
                .userRoles(Set.of(userRoleMapping))
                .build();
    }

    @Test
    @DisplayName("Should generate valid access token with correct claims")
    void shouldGenerateValidAccessToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();

        UUID extractedUserId = jwtService.extractUserId(token);
        assertThat(extractedUserId).isEqualTo(testUserId);

        Claims claims = jwtService.extractClaims(token);
        assertThat(claims.getSubject()).isEqualTo(testUserId.toString());
        assertThat(claims.getIssuer()).isEqualTo("http://localhost:8080");
        assertThat(claims.get("username", String.class)).isEqualTo("testuser");
        assertThat(claims.get("email", String.class)).isEqualTo("testuser@omragul.com");

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles).containsExactly("USER");
    }

    @Test
    @DisplayName("Should reject tampered token")
    void shouldRejectTamperedToken() {
        String token = jwtService.generateAccessToken(testUser);
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        JwtServiceImpl expiredJwtService = new JwtServiceImpl(
                privateKeyResource,
                publicKeyResource,
                -1, // expired 1 minute ago
                "http://localhost:8080"
        );

        String expiredToken = expiredJwtService.generateAccessToken(testUser);
        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }
}
