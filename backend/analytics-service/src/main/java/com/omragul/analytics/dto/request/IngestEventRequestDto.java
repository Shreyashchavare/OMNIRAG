package com.omragul.analytics.dto.request;

import com.omragul.analytics.enums.EventStatus;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.enums.SourceService;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound DTO for the internal analytics event ingestion endpoint.
 * <p>
 * Rules:
 * <ul>
 *   <li>eventId — producer-generated idempotency key; must be a valid UUID.</li>
 *   <li>eventType — must be one of the allowlisted EventType values.</li>
 *   <li>occurredAt — must not be in the future by more than 60 seconds,
 *       and must not be older than 30 days.</li>
 *   <li>inputTokens / outputTokens / durationMs — must be non-negative if present.</li>
 *   <li>modelName — max 128 chars.</li>
 *   <li>resourceId — max 256 chars.</li>
 * </ul>
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestEventRequestDto {

    @NotNull(message = "eventId is required")
    private UUID eventId;

    @NotNull(message = "eventType is required")
    private EventType eventType;

    @NotNull(message = "sourceService is required")
    private SourceService sourceService;

    @NotNull(message = "occurredAt is required")
    private Instant occurredAt;

    private UUID correlationId;

    /** Opaque actor reference. The Analytics Service does not validate this against Identity. */
    private UUID actorId;

    @NotNull(message = "status is required")
    private EventStatus status;

    @Min(value = 0, message = "durationMs must be non-negative")
    private Long durationMs;

    @Min(value = 0, message = "inputTokens must be non-negative")
    private Integer inputTokens;

    @Min(value = 0, message = "outputTokens must be non-negative")
    private Integer outputTokens;

    @Size(max = 128, message = "modelName must not exceed 128 characters")
    private String modelName;

    @Size(max = 256, message = "resourceId must not exceed 256 characters")
    private String resourceId;
}
