package com.omragul.analytics.controller;

import com.omragul.analytics.dto.request.IngestEventRequestDto;
import com.omragul.analytics.dto.response.IngestEventResponseDto;
import com.omragul.analytics.service.EventIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal event ingestion endpoint.
 * Protected by RS256 JWT Bearer token authentication.
 * Callers with ROLE_INTERNAL or ROLE_ADMIN are permitted.
 */
@RestController
@RequestMapping("/internal/analytics")
@RequiredArgsConstructor
public class InternalEventIngestionController {

    private final EventIngestionService eventIngestionService;

    /**
     * POST /internal/analytics/events
     * <p>
     * Ingests a single analytics event. Idempotent: if the same {@code eventId}
     * is submitted again, the existing record is returned with {@code duplicate: true}.
     * </p>
     */
    @PostMapping("/events")
    @PreAuthorize("hasAnyRole('INTERNAL', 'ADMIN')")
    public ResponseEntity<IngestEventResponseDto> ingestEvent(
            @Valid @RequestBody IngestEventRequestDto request
    ) {
        IngestEventResponseDto response = eventIngestionService.ingestEvent(request);

        // 201 for new events, 200 for idempotent duplicates
        HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
