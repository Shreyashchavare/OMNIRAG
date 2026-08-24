package com.omragul.identity.service.security;

import com.omragul.identity.entity.user.User;
import io.jsonwebtoken.Claims;

import java.util.UUID;

public interface JwtService {

    String generateAccessToken(User user);

    Claims extractClaims(String token);

    UUID extractUserId(String token);

    boolean isTokenValid(String token);
}