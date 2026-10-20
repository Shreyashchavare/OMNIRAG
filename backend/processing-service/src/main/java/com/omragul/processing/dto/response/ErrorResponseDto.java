package com.omragul.processing.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response DTO")
public class ErrorResponseDto {

    @Schema(description = "Error timestamp")
    private Instant timestamp;

    @Schema(description = "HTTP status code")
    private int status;

    @Schema(description = "High-level error classification code")
    private String error;

    @Schema(description = "Safe, informative error message")
    private String message;

    @Schema(description = "Request URI path")
    private String path;

    @Schema(description = "Detailed validation field errors if applicable")
    private List<String> details;
}
