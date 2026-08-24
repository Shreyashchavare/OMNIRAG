package com.omragul.identity.dto.response.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class ErrorResponseDto {

    private Instant timestamp;

    private int status;

    private String error;

    private String message;

    private String path;
}