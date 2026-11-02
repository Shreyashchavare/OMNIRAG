package com.omragul.identity.dto.request.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequestDto {

    @JsonAlias({"refresh_token", "refreshToken"})
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}