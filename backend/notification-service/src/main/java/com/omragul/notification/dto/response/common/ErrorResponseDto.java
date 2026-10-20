package com.omragul.notification.dto.response.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard error response DTO matching OMRAGUL conventions across all services.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponseDto {

    private Instant timestamp;

    private int status;

    private String error;

    private String message;

    private String path;
}
