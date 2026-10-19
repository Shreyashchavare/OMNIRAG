package com.omragul.analytics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Document analytics summary for the requested period.
 */
@Getter
@Builder
public class DocumentAnalyticsResponseDto {

    private Instant generatedAt;
    private Instant periodFrom;
    private Instant periodTo;

    private long totalDocumentEvents;
    private long uploaded;
    private long processed;
    private long failed;
    private long deleted;
}
