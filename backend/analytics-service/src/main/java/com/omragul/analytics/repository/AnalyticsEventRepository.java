package com.omragul.analytics.repository;

import com.omragul.analytics.entity.AnalyticsEvent;
import com.omragul.analytics.enums.EventStatus;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.enums.SourceService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    /** Idempotency check — returns the existing event if already ingested. */
    Optional<AnalyticsEvent> findByEventId(UUID eventId);

    // -----------------------------------------------------------------------
    // Dashboard / aggregate counts
    // -----------------------------------------------------------------------

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.occurredAt BETWEEN :from AND :to
            """)
    long countByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.eventType IN (
                com.omragul.analytics.enums.EventType.DOCUMENT_UPLOADED,
                com.omragul.analytics.enums.EventType.DOCUMENT_PROCESSED,
                com.omragul.analytics.enums.EventType.DOCUMENT_FAILED,
                com.omragul.analytics.enums.EventType.DOCUMENT_DELETED
            )
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countDocumentEventsByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.eventType IN (
                com.omragul.analytics.enums.EventType.QUERY_EXECUTED,
                com.omragul.analytics.enums.EventType.QUERY_SUCCEEDED,
                com.omragul.analytics.enums.EventType.QUERY_FAILED
            )
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countQueryEventsByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.eventType = com.omragul.analytics.enums.EventType.MODEL_USAGE
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countModelUsageByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    // -----------------------------------------------------------------------
    // Document-specific aggregations
    // -----------------------------------------------------------------------

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.eventType = :eventType
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countByEventTypeAndPeriod(
            @Param("eventType") EventType eventType,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.eventType = :eventType
            AND e.status = :status
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countByEventTypeAndStatusAndPeriod(
            @Param("eventType") EventType eventType,
            @Param("status") EventStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // -----------------------------------------------------------------------
    // Query-specific aggregations
    // -----------------------------------------------------------------------

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.eventType IN (
                com.omragul.analytics.enums.EventType.QUERY_EXECUTED,
                com.omragul.analytics.enums.EventType.QUERY_SUCCEEDED,
                com.omragul.analytics.enums.EventType.QUERY_FAILED
            )
            AND e.status = :status
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countQueryEventsByStatusAndPeriod(
            @Param("status") EventStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            SELECT AVG(e.durationMs) FROM AnalyticsEvent e
            WHERE e.eventType IN (
                com.omragul.analytics.enums.EventType.QUERY_EXECUTED,
                com.omragul.analytics.enums.EventType.QUERY_SUCCEEDED,
                com.omragul.analytics.enums.EventType.QUERY_FAILED
            )
            AND e.durationMs IS NOT NULL
            AND e.occurredAt BETWEEN :from AND :to
            """)
    Double avgQueryDurationMs(@Param("from") Instant from, @Param("to") Instant to);

    // -----------------------------------------------------------------------
    // Usage (token) aggregations
    // -----------------------------------------------------------------------

    @Query("""
            SELECT COALESCE(SUM(e.inputTokens), 0) FROM AnalyticsEvent e
            WHERE e.eventType = com.omragul.analytics.enums.EventType.MODEL_USAGE
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long sumInputTokensByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COALESCE(SUM(e.outputTokens), 0) FROM AnalyticsEvent e
            WHERE e.eventType = com.omragul.analytics.enums.EventType.MODEL_USAGE
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long sumOutputTokensByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(DISTINCT e.modelName) FROM AnalyticsEvent e
            WHERE e.eventType = com.omragul.analytics.enums.EventType.MODEL_USAGE
            AND e.modelName IS NOT NULL
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countDistinctModelsByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    // -----------------------------------------------------------------------
    // User activity aggregations
    // -----------------------------------------------------------------------

    @Query("""
            SELECT COUNT(DISTINCT e.actorId) FROM AnalyticsEvent e
            WHERE e.actorId IS NOT NULL
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countDistinctActiveUsersByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.eventType = com.omragul.analytics.enums.EventType.USER_REGISTERED
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countUserRegistrationsByPeriod(@Param("from") Instant from, @Param("to") Instant to);

    // -----------------------------------------------------------------------
    // Source service filter
    // -----------------------------------------------------------------------

    @Query("""
            SELECT COUNT(e) FROM AnalyticsEvent e
            WHERE e.sourceService = :sourceService
            AND e.occurredAt BETWEEN :from AND :to
            """)
    long countBySourceServiceAndPeriod(
            @Param("sourceService") SourceService sourceService,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
