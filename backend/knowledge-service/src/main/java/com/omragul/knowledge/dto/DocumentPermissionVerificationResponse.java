package com.omragul.knowledge.dto;

import java.util.UUID;

public record DocumentPermissionVerificationResponse(
        UUID documentId,
        UUID userId,
        String permission,
        boolean authorized
) {}
