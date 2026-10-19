package com.omragul.analytics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * User activity analytics summary for the requested period.
 * <p>
 * IMPORTANT: This metric reflects only events that carry an actorId in the analytics
 * event log. It is NOT a substitute for the Identity Service user-management API.
 * The total registered user count is NOT available here (pending Identity integration).
 * </p>
 */
@Getter
@Builder
public class UserAnalyticsResponseDto {

    private Instant generatedAt;
    private Instant periodFrom;
    private Instant periodTo;

    /**
     * Count of distinct actorIds that appear in analytics events.
     * Reflects observed activity only — not total user count.
     */
    private long activeUsers;

    /**
     * Count of USER_REGISTERED events in the period.
     * Requires producers to publish USER_REGISTERED events with the actorId.
     */
    private long newRegistrations;

    /**
     * Describes what this metric represents and which integrations are pending.
     */
    private String note;
}
