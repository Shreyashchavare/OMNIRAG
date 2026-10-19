package com.omragul.analytics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * AI model usage analytics summary for the requested period.
 */
@Getter
@Builder
public class UsageAnalyticsResponseDto {

    private Instant generatedAt;
    private Instant periodFrom;
    private Instant periodTo;

    private long modelUsageRequests;
    private long totalInputTokens;
    private long totalOutputTokens;
    private long totalTokens;
    private long distinctModels;

    /**
     * Note about data availability.
     * Present when token data is absent (producers not yet publishing MODEL_USAGE events).
     */
    private String note;
}
