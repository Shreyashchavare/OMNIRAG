package com.omragul.analytics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Response returned after successful event ingestion.
 */
@Getter
@Builder
public class IngestEventResponseDto {

    /** The internal analytics record ID. */
    private Long id;

    /** The producer-supplied idempotency key echoed back. */
    private UUID eventId;

    /** When this record was received by the Analytics Service. */
    private Instant receivedAt;

    /**
     * True if this eventId was already present (idempotent duplicate).
     * The existing record was returned unchanged.
     */
    private boolean duplicate;
}
