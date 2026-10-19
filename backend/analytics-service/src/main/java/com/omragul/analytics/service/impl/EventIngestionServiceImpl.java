package com.omragul.analytics.service.impl;

import com.omragul.analytics.dto.request.IngestEventRequestDto;
import com.omragul.analytics.dto.response.IngestEventResponseDto;
import com.omragul.analytics.entity.AnalyticsEvent;
import com.omragul.analytics.exception.InvalidEventException;
import com.omragul.analytics.repository.AnalyticsEventRepository;
import com.omragul.analytics.service.EventIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Validates and persists analytics events.
 * <p>
 * Business rules enforced here (beyond Jakarta Bean Validation):
 * <ul>
 *   <li>occurredAt must not be in the future by more than 60 seconds (clock skew tolerance).</li>
 *   <li>occurredAt must not be more than 30 days in the past.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestionServiceImpl implements EventIngestionService {

    /** Maximum allowed clock skew — events from "the future" by this margin are accepted. */
    private static final Duration MAX_FUTURE_SKEW = Duration.ofSeconds(60);

    /** Maximum age of accepted events. */
    private static final Duration MAX_EVENT_AGE = Duration.ofDays(30);

    private final AnalyticsEventRepository eventRepository;

    @Override
    @Transactional
    public IngestEventResponseDto ingestEvent(IngestEventRequestDto request) {

        // Idempotency check
        Optional<AnalyticsEvent> existing = eventRepository.findByEventId(request.getEventId());
        if (existing.isPresent()) {
            AnalyticsEvent event = existing.get();
            log.debug("Duplicate event received: eventId={}", request.getEventId());
            return IngestEventResponseDto.builder()
                    .id(event.getId())
                    .eventId(event.getEventId())
                    .receivedAt(event.getReceivedAt())
                    .duplicate(true)
                    .build();
        }

        // Timestamp validation
        validateTimestamp(request.getOccurredAt());

        Instant now = Instant.now();

        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventId(request.getEventId())
                .eventType(request.getEventType())
                .sourceService(request.getSourceService())
                .occurredAt(request.getOccurredAt())
                .receivedAt(now)
                .correlationId(request.getCorrelationId())
                .actorId(request.getActorId())
                .status(request.getStatus())
                .durationMs(request.getDurationMs())
                .inputTokens(request.getInputTokens())
                .outputTokens(request.getOutputTokens())
                .modelName(request.getModelName())
                .resourceId(request.getResourceId())
                .build();

        AnalyticsEvent saved = eventRepository.save(event);

        log.info("Analytics event ingested: id={}, type={}, source={}",
                saved.getId(), saved.getEventType(), saved.getSourceService());

        return IngestEventResponseDto.builder()
                .id(saved.getId())
                .eventId(saved.getEventId())
                .receivedAt(saved.getReceivedAt())
                .duplicate(false)
                .build();
    }

    private void validateTimestamp(Instant occurredAt) {
        Instant now = Instant.now();

        if (occurredAt.isAfter(now.plus(MAX_FUTURE_SKEW))) {
            throw new InvalidEventException(
                    "occurredAt is too far in the future. Maximum allowed clock skew is 60 seconds.");
        }

        if (occurredAt.isBefore(now.minus(MAX_EVENT_AGE))) {
            throw new InvalidEventException(
                    "occurredAt is more than 30 days in the past. Stale events are not accepted.");
        }
    }
}
