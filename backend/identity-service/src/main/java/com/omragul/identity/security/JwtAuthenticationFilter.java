package com.omragul.identity.security;

import com.omragul.identity.service.security.JwtService;
import com.omragul.identity.service.security.RbacAuthorizationService;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RbacAuthorizationService rbacAuthorizationService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        // No Authorization header
        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT
        String token = authorizationHeader.substring(7);

        // Validate JWT
        if (!jwtService.isTokenValid(token)) {

            filterChain.doFilter(request, response);
            return;
        }

        // Extract user ID from JWT
        UUID userId =
                jwtService.extractUserId(token);

        // Get user's permissions
        Set<String> permissions =
                rbacAuthorizationService
                        .getUserPermissions(userId);

        // Get user's roles (required for Object-Level-Authorization)
        Set<String> roles =
                rbacAuthorizationService.getUserRoles(userId);

        // Convert permissions and roles to Spring authorities
        var authorities = Stream.concat(
                        permissions.stream(),
                        roles.stream()
                                .map(role -> "ROLE_" + role)
                )
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        // Create authenticated user
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );

        // Store authentication
        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}