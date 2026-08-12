package com.omragul.identity.dto.request.auth;

import com.omragul.identity.enums.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequestDto {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "OTP code is required")
    private String otpCode;

    @NotNull(message = "OTP purpose is required")
    private OtpPurpose purpose;
}