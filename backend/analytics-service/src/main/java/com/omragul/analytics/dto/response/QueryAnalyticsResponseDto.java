package com.omragul.analytics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Query (RAG) analytics summary for the requested period.
 */
@Getter
@Builder
public class QueryAnalyticsResponseDto {

    private Instant generatedAt;
    private Instant periodFrom;
    private Instant periodTo;

    private long totalQueryEvents;
    private long succeeded;
    private long failed;

    /** Average query duration in milliseconds; null when no latency data exists. */
    private Double avgDurationMs;

    /** Note about data availability (e.g. when latency data is absent). */
    private String note;
}
