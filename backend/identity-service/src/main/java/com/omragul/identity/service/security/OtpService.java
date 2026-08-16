package com.omragul.identity.service.security;

import com.omragul.identity.enums.OtpPurpose;

import java.util.UUID;

public interface OtpService {

    void generateOtp(
            UUID userId,
            OtpPurpose purpose
    );

    boolean verifyOtp(
            UUID userId,
            OtpPurpose purpose,
            String otpCode
    );

    void invalidateOtp(
            UUID userId,
            OtpPurpose purpose
    );
}