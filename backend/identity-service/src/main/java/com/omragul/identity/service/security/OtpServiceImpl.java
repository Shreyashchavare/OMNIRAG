package com.omragul.identity.service.security;

import com.omragul.identity.entity.auth.Otp;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.enums.OtpPurpose;
import com.omragul.identity.exception.ResourceNotFoundException;
import com.omragul.identity.repository.OtpRepository;
import com.omragul.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${identity.otp.expiration-minutes}")
    private long otpExpirationMinutes;

    @Override
    @Transactional
    public void generateOtp(
            UUID userId,
            OtpPurpose purpose
    ) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        // Invalidate previous unverified OTPs
        List<Otp> existingOtps = otpRepository
                .findByUserUserIdAndPurposeAndVerifiedFalse(
                        userId,
                        purpose
                );

        for (Otp otp : existingOtps) {
            otp.setVerified(true);
        }

        otpRepository.saveAll(existingOtps);

        // Generate new OTP
        String otpCode = generateOtpCode();

        // Hash OTP before storing it
        String otpCodeHash = passwordEncoder.encode(otpCode);

        // OTP expires after 5 minutes
        Instant expiresAt = Instant.now()
                .plus(otpExpirationMinutes, ChronoUnit.MINUTES);

        Otp otp = Otp.builder()
                .user(user)
                .otpCodeHash(otpCodeHash)
                .purpose(purpose)
                .expiresAt(expiresAt)
                .verified(false)
                .build();

        otpRepository.save(otp);

        // For now, we don't have an email/SMS service.
        // The generated OTP will eventually be sent through
        // NotificationService.
        System.out.println("Generated OTP: " + otpCode);
    }

    @Override
    @Transactional
    public boolean verifyOtp(
            UUID userId,
            OtpPurpose purpose,
            String otpCode
    ) {

        Instant currentTime = Instant.now();

        Otp otp = otpRepository
                .findTopByUserUserIdAndPurposeOrderByCreatedAtDesc(
                        userId,
                        purpose
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "OTP not found for user id: " + userId
                        )
                );

        // Check if OTP is already used
        if (otp.getVerified()) {
            return false;
        }

        // Check expiration
        if (otp.getExpiresAt().isBefore(currentTime)) {
            return false;
        }

        // Compare entered OTP with stored hash
        boolean matches = passwordEncoder.matches(
                otpCode,
                otp.getOtpCodeHash()
        );

        if (!matches) {
            return false;
        }

        // OTP successfully verified
        otp.setVerified(true);

        otpRepository.save(otp);

        return true;
    }

    @Override
    @Transactional
    public void invalidateOtp(
            UUID userId,
            OtpPurpose purpose
    ) {

        List<Otp> existingOtps = otpRepository
                .findByUserUserIdAndPurposeAndVerifiedFalse(
                        userId,
                        purpose
                );

        for (Otp otp : existingOtps) {
            otp.setVerified(true);
        }

        otpRepository.saveAll(existingOtps);
    }

    private String generateOtpCode() {

        return String.format(
                "%06d",
                ThreadLocalRandom.current()
                        .nextInt(0, 1_000_000)
        );
    }
}