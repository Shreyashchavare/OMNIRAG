package com.omragul.analytics.service;

import com.omragul.analytics.dto.request.IngestEventRequestDto;
import com.omragul.analytics.dto.response.IngestEventResponseDto;

public interface EventIngestionService {

    /**
     * Ingests an analytics event.
     * <p>
     * If an event with the same {@code eventId} already exists, returns the
     * existing record (idempotent) with {@code duplicate=true}.
     * </p>
     */
    IngestEventResponseDto ingestEvent(IngestEventRequestDto request);
}
