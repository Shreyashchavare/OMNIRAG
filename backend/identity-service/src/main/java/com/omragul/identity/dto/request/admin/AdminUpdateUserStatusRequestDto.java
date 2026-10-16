package com.omragul.identity.dto.request.admin;

import com.omragul.identity.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserStatusRequestDto {

    @NotNull(message = "User status is required")
    private UserStatus status;
}