package com.omragul.processing.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.UUID;

public record DocumentPermissionVerificationResponse(
        @JsonAlias({"documentId", "document_id"})
        UUID documentId,

        @JsonAlias({"userId", "user_id"})
        UUID userId,

        String permission,
        boolean authorized
) {}
