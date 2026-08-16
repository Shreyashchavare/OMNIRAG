package com.omragul.identity.service.security;

import com.omragul.identity.entity.auth.RefreshToken;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.exception.ResourceNotFoundException;
import com.omragul.identity.repository.RefreshTokenRepository;
import com.omragul.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${identity.refresh-token.expiration-days}")
    private long refreshTokenExpirationDays;

    @Override
    @Transactional
    public String createToken(
            UUID userId,
            String deviceName,
            String ipAddress,
            String userAgent
    ) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        // Generate the raw refresh token
        String rawToken = generateToken();

        // Store only the SHA-256 hash
        String tokenHash = hashToken(rawToken);

        // Calculate expiration time
        Instant expiresAt = Instant.now()
                .plus(refreshTokenExpirationDays, ChronoUnit.DAYS);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .deviceName(deviceName)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        refreshTokenRepository.save(refreshToken);

        // Return the raw token.
        // It is never stored in the database.
        return rawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken validateToken(String token) {

        String tokenHash = hashToken(token);

        Instant currentTime = Instant.now();

        return refreshTokenRepository
                .findByTokenHashAndRevokedFalseAndExpiresAtAfter(
                        tokenHash,
                        currentTime
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Invalid or expired refresh token"
                        )
                );
    }

    @Override
    @Transactional
    public void revokeToken(String token) {

        String tokenHash = hashToken(token);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Refresh token not found or already revoked"
                        )
                );

        refreshToken.setRevoked(true);

        refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(UUID userId) {

        // First make sure the user exists
        userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        List<RefreshToken> refreshTokens =
                refreshTokenRepository.findByUserUserId(userId);

        for (RefreshToken refreshToken : refreshTokens) {
            refreshToken.setRevoked(true);
        }

        refreshTokenRepository.saveAll(refreshTokens);
    }

    private String generateToken() {

        byte[] randomBytes = new byte[64];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashToken(String token) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return java.util.HexFormat
                    .of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    e
            );
        }
    }
}