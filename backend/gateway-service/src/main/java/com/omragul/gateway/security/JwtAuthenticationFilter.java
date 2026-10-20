package com.omragul.gateway.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filter that intercepts incoming HTTP requests, verifies JWT Bearer tokens,
 * establishes Spring Security context, and extracts user metadata for downstream forwarding.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "X-User-Id";
    public static final String ATTR_USER_ROLES = "X-User-Roles";
    public static final String ATTR_USER_EMAIL = "X-User-Email";
    public static final String ATTR_USER_NAME = "X-User-Name";

    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            if (jwtValidator.isTokenValid(token)) {
                try {
                    Claims claims = jwtValidator.extractClaims(token);
                    UUID userId = jwtValidator.extractUserId(claims);
                    List<String> roles = jwtValidator.extractRoles(claims);
                    String username = jwtValidator.extractUsername(claims);
                    String email = jwtValidator.extractEmail(claims);

                    var authorities = roles.stream()
                            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, token, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Propagate user metadata attributes for proxy header forwarding
                    request.setAttribute(ATTR_USER_ID, userId.toString());
                    request.setAttribute(ATTR_USER_ROLES, String.join(",", roles));
                    if (email != null && !email.isBlank()) {
                        request.setAttribute(ATTR_USER_EMAIL, email);
                    }
                    if (username != null && !username.isBlank()) {
                        request.setAttribute(ATTR_USER_NAME, username);
                    }
                } catch (Exception ignored) {
                    // Invalid claims or invalid UUID format; context remains unauthenticated
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
