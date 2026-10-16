package com.omragul.identity.dto.request.admin;

import com.omragul.identity.dto.request.user.SignupProfileRequestDto;
import com.omragul.identity.enums.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateUserRequestDto {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Temporary password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String temporaryPassword;

    @Valid
    @NotNull(message = "Profile is required")
    private SignupProfileRequestDto profile;

    @NotNull(message = "User status is required")
    private UserStatus status;
}