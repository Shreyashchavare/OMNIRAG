package com.omragul.identity.dto.request.rbac;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequestDto {

    @NotNull(message = "Role ID is required")
    private UUID roleId;
}