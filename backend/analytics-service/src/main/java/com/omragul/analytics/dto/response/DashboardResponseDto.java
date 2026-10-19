package com.omragul.analytics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Dashboard summary response.
 * {@code dataStatus} tells the caller which metrics are actually backed by data:
 * <ul>
 *   <li>AVAILABLE — all sub-metrics are from stored events.</li>
 *   <li>EMPTY — the analytics database has no events yet.</li>
 *   <li>PARTIALLY_AVAILABLE — some metrics are available; others are pending upstream integration.</li>
 *   <li>UPSTREAM_UNAVAILABLE — a required upstream source is down or not yet integrated.</li>
 * </ul>
 */
@Getter
@Builder
public class DashboardResponseDto {

    private Instant generatedAt;

    /** Indicates availability/quality of the returned metrics. */
    private String dataStatus;

    /** ISO-8601 start of the summary period. */
    private Instant periodFrom;

    /** ISO-8601 end of the summary period. */
    private Instant periodTo;

    private DocumentSummary documents;
    private QuerySummary queries;
    private UsageSummary usage;
    private UserSummary users;

    @Getter
    @Builder
    public static class DocumentSummary {
        private long total;
        private long processed;
        private long failed;
        private long deleted;
    }

    @Getter
    @Builder
    public static class QuerySummary {
        private long total;
        private long succeeded;
        private long failed;
        /** Average query duration in milliseconds; null if no latency data is available. */
        private Double avgDurationMs;
    }

    @Getter
    @Builder
    public static class UsageSummary {
        private long modelUsageRequests;
        private long totalInputTokens;
        private long totalOutputTokens;
        private long totalTokens;
        private long distinctModels;
    }

    @Getter
    @Builder
    public static class UserSummary {
        /**
         * Count of distinct actorIds that appear in analytics events for the period.
         * Does NOT reflect the total registered user count from Identity Service
         * (that integration is pending — see README).
         */
        private long activeUsers;

        private long newRegistrations;

        /**
         * Human-readable note explaining the data source.
         */
        private String note;
    }
}
