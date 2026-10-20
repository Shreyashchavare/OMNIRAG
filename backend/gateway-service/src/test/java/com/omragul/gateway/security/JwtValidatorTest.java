package com.omragul.gateway.security;

import com.omragul.gateway.util.TestJwtFactory;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private JwtValidator jwtValidator;

    @BeforeEach
    void setUp() {
        jwtValidator = new JwtValidator(
                new ClassPathResource("certs/test_public_key.pem"),
                "http://localhost:8080"
        );
    }

    @Test
    @DisplayName("Valid token passes validation and claims can be extracted")
    void testValidToken() {
        UUID userId = UUID.randomUUID();
        String token = TestJwtFactory.generateValidToken(userId, List.of("ROLE_USER", "ROLE_ADMIN"));

        assertThat(jwtValidator.isTokenValid(token)).isTrue();

        Claims claims = jwtValidator.extractClaims(token);
        assertThat(jwtValidator.extractUserId(claims)).isEqualTo(userId);
        assertThat(jwtValidator.extractUsername(claims)).isEqualTo("testuser");
        assertThat(jwtValidator.extractEmail(claims)).isEqualTo("test@example.com");
        assertThat(jwtValidator.extractRoles(claims)).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Expired token is rejected")
    void testExpiredToken() {
        UUID userId = UUID.randomUUID();
        String expiredToken = TestJwtFactory.generateExpiredToken(userId);

        assertThat(jwtValidator.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("Token with unexpected issuer is rejected")
    void testInvalidIssuer() {
        UUID userId = UUID.randomUUID();
        String token = TestJwtFactory.generateInvalidIssuerToken(userId);

        assertThat(jwtValidator.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("Malformed token string is rejected")
    void testMalformedToken() {
        assertThat(jwtValidator.isTokenValid("not.a.valid.jwt")).isFalse();
        assertThat(jwtValidator.isTokenValid("")).isFalse();
        assertThat(jwtValidator.isTokenValid(null)).isFalse();
    }
}
