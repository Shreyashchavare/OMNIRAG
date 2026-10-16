package com.omragul.identity.service.security;

import com.omragul.identity.entity.auth.RefreshToken;

import java.util.UUID;

public interface RefreshTokenService {

    String createToken(
            UUID userId,
            String deviceName,
            String ipAddress,
            String userAgent
    );

    RefreshToken validateToken(String token);

    void revokeToken(String token);

    void revokeAllUserTokens(UUID userId);
}