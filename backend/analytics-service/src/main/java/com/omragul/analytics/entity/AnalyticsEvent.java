package com.omragul.analytics.entity;

import com.omragul.analytics.enums.EventStatus;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.enums.SourceService;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable analytics event record.
 * Each row represents one observed business event.
 * This table is append-only; events must not be mutated after ingestion.
 */
@Entity
@Table(name = "analytics_events")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Producer-generated idempotency key. Must be unique across all events. */
    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "event_type", nullable = false, updatable = false,
            columnDefinition = "event_type")
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_service", nullable = false, updatable = false,
            columnDefinition = "source_service")
    private SourceService sourceService;

    /** When the event occurred in the producer service (producer-supplied). */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** When Analytics Service received this event. */
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    /** Optional correlation identifier for distributed tracing. */
    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    /**
     * Opaque actor reference (e.g. a user UUID).
     * Stored as-is; not a foreign key to Identity tables.
     */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, updatable = false,
            columnDefinition = "event_status")
    private EventStatus status;

    /** Duration of the operation in milliseconds (optional, non-negative). */
    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    /** Input token count for model usage events (optional, non-negative). */
    @Column(name = "input_tokens", updatable = false)
    private Integer inputTokens;

    /** Output token count for model usage events (optional, non-negative). */
    @Column(name = "output_tokens", updatable = false)
    private Integer outputTokens;

    /** Model identifier for model usage events (optional, length-limited). */
    @Column(name = "model_name", length = 128, updatable = false)
    private String modelName;

    /** Opaque resource identifier (e.g. document ID, query ID). */
    @Column(name = "resource_id", length = 256, updatable = false)
    private String resourceId;
}
