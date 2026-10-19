package com.omragul.analytics.repository;

import com.omragul.analytics.entity.AnalyticsEvent;
import com.omragul.analytics.enums.EventStatus;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.enums.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence integration tests using Testcontainers PostgreSQL.
 * Tests run against a real database with Flyway migrations applied.
 */
@DataJpaTest
@Testcontainers
class AnalyticsEventRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("analytics_test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private AnalyticsEventRepository repository;

    private Instant from;
    private Instant to;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        to = Instant.now();
        from = to.minus(30, ChronoUnit.DAYS);
    }

    private AnalyticsEvent buildEvent(EventType type, EventStatus status) {
        return AnalyticsEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(type)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(10))
                .receivedAt(Instant.now())
                .status(status)
                .build();
    }

    @Test
    void findByEventId_whenExists_returnsEvent() {
        UUID eventId = UUID.randomUUID();
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventId(eventId)
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .build();
        repository.save(event);

        Optional<AnalyticsEvent> found = repository.findByEventId(eventId);

        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo(eventId);
    }

    @Test
    void findByEventId_whenNotExists_returnsEmpty() {
        Optional<AnalyticsEvent> found = repository.findByEventId(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void countDocumentEventsByPeriod_countsOnlyDocumentEvents() {
        repository.save(buildEvent(EventType.DOCUMENT_UPLOADED, EventStatus.SUCCESS));
        repository.save(buildEvent(EventType.DOCUMENT_PROCESSED, EventStatus.SUCCESS));
        repository.save(buildEvent(EventType.QUERY_SUCCEEDED, EventStatus.SUCCESS));

        long count = repository.countDocumentEventsByPeriod(from, to);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void countQueryEventsByPeriod_countsOnlyQueryEvents() {
        repository.save(buildEvent(EventType.QUERY_EXECUTED, EventStatus.SUCCESS));
        repository.save(buildEvent(EventType.QUERY_FAILED, EventStatus.FAILURE));
        repository.save(buildEvent(EventType.DOCUMENT_UPLOADED, EventStatus.SUCCESS));

        long count = repository.countQueryEventsByPeriod(from, to);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void sumInputTokensByPeriod_sumsOnlyModelUsageEvents() {
        AnalyticsEvent usageEvent1 = AnalyticsEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(EventType.MODEL_USAGE)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(Instant.now().minusSeconds(10))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .inputTokens(100)
                .outputTokens(50)
                .build();

        AnalyticsEvent usageEvent2 = AnalyticsEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(EventType.MODEL_USAGE)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .inputTokens(200)
                .outputTokens(80)
                .build();

        // Non-usage event — should not affect token sums
        AnalyticsEvent nonUsage = AnalyticsEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(3))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .build();

        repository.save(usageEvent1);
        repository.save(usageEvent2);
        repository.save(nonUsage);

        assertThat(repository.sumInputTokensByPeriod(from, to)).isEqualTo(300L);
        assertThat(repository.sumOutputTokensByPeriod(from, to)).isEqualTo(130L);
    }

    @Test
    void countDistinctActiveUsersByPeriod_countsDistinctActorIds() {
        UUID actor1 = UUID.randomUUID();
        UUID actor2 = UUID.randomUUID();

        // actor1 has two events — should be counted once
        AnalyticsEvent e1 = AnalyticsEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(EventType.QUERY_SUCCEEDED)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(Instant.now().minusSeconds(20))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .actorId(actor1)
                .build();

        AnalyticsEvent e2 = AnalyticsEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(EventType.QUERY_SUCCEEDED)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(Instant.now().minusSeconds(10))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .actorId(actor1)
                .build();

        AnalyticsEvent e3 = AnalyticsEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(EventType.QUERY_SUCCEEDED)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .actorId(actor2)
                .build();

        repository.save(e1);
        repository.save(e2);
        repository.save(e3);

        long distinctUsers = repository.countDistinctActiveUsersByPeriod(from, to);
        assertThat(distinctUsers).isEqualTo(2L);
    }

    @Test
    void duplicateEventId_throwsConstraintViolation() {
        UUID eventId = UUID.randomUUID();

        AnalyticsEvent first = AnalyticsEvent.builder()
                .eventId(eventId)
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(10))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .build();

        repository.saveAndFlush(first);

        AnalyticsEvent duplicate = AnalyticsEvent.builder()
                .eventId(eventId)
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .receivedAt(Instant.now())
                .status(EventStatus.SUCCESS)
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> repository.saveAndFlush(duplicate),
                "Expected unique constraint violation on event_id"
        );
    }
}
