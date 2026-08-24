package com.omragul.identity.dto.response.rbac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthorizationResponseDto {

    private UUID userId;

    private Set<String> roles;

    private Set<String> permissions;
}