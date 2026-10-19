package com.omragul.analytics.service;

import com.omragul.analytics.dto.request.IngestEventRequestDto;
import com.omragul.analytics.dto.response.IngestEventResponseDto;
import com.omragul.analytics.entity.AnalyticsEvent;
import com.omragul.analytics.enums.EventStatus;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.enums.SourceService;
import com.omragul.analytics.exception.InvalidEventException;
import com.omragul.analytics.repository.AnalyticsEventRepository;
import com.omragul.analytics.service.impl.EventIngestionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventIngestionServiceTest {

    @Mock
    private AnalyticsEventRepository eventRepository;

    private EventIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new EventIngestionServiceImpl(eventRepository);
    }

    private IngestEventRequestDto buildValidRequest(UUID eventId) {
        return IngestEventRequestDto.builder()
                .eventId(eventId)
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(10))
                .status(EventStatus.SUCCESS)
                .build();
    }

    @Test
    void ingestEvent_newEvent_persistsAndReturnsNonDuplicate() {
        UUID eventId = UUID.randomUUID();
        IngestEventRequestDto request = buildValidRequest(eventId);

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        AnalyticsEvent savedEvent = AnalyticsEvent.builder()
                .id(1L)
                .eventId(eventId)
                .receivedAt(Instant.now())
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(request.getOccurredAt())
                .status(EventStatus.SUCCESS)
                .build();

        when(eventRepository.save(any(AnalyticsEvent.class))).thenReturn(savedEvent);

        IngestEventResponseDto result = ingestionService.ingestEvent(request);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getId()).isEqualTo(1L);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.DOCUMENT_UPLOADED);
    }

    @Test
    void ingestEvent_duplicateEventId_returnsDuplicateWithoutPersisting() {
        UUID eventId = UUID.randomUUID();
        IngestEventRequestDto request = buildValidRequest(eventId);

        AnalyticsEvent existing = AnalyticsEvent.builder()
                .id(42L)
                .eventId(eventId)
                .receivedAt(Instant.now().minusSeconds(30))
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(request.getOccurredAt())
                .status(EventStatus.SUCCESS)
                .build();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));

        IngestEventResponseDto result = ingestionService.ingestEvent(request);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getId()).isEqualTo(42L);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void ingestEvent_occurredAtTooFarInFuture_throwsInvalidEventException() {
        UUID eventId = UUID.randomUUID();
        IngestEventRequestDto request = IngestEventRequestDto.builder()
                .eventId(eventId)
                .eventType(EventType.QUERY_SUCCEEDED)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(Instant.now().plus(2, ChronoUnit.MINUTES))
                .status(EventStatus.SUCCESS)
                .build();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.ingestEvent(request))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("future");
    }

    @Test
    void ingestEvent_occurredAtTooOld_throwsInvalidEventException() {
        UUID eventId = UUID.randomUUID();
        IngestEventRequestDto request = IngestEventRequestDto.builder()
                .eventId(eventId)
                .eventType(EventType.QUERY_SUCCEEDED)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(Instant.now().minus(31, ChronoUnit.DAYS))
                .status(EventStatus.SUCCESS)
                .build();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.ingestEvent(request))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("30 days");
    }

    @Test
    void ingestEvent_withOptionalFields_persistsAllFields() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        IngestEventRequestDto request = IngestEventRequestDto.builder()
                .eventId(eventId)
                .eventType(EventType.MODEL_USAGE)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .correlationId(correlationId)
                .actorId(actorId)
                .status(EventStatus.SUCCESS)
                .durationMs(150L)
                .inputTokens(100)
                .outputTokens(200)
                .modelName("llama3.2")
                .resourceId("query-abc-123")
                .build();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        AnalyticsEvent savedEvent = AnalyticsEvent.builder()
                .id(2L)
                .eventId(eventId)
                .receivedAt(Instant.now())
                .eventType(EventType.MODEL_USAGE)
                .sourceService(SourceService.RAG_SERVICE)
                .occurredAt(request.getOccurredAt())
                .correlationId(correlationId)
                .actorId(actorId)
                .status(EventStatus.SUCCESS)
                .durationMs(150L)
                .inputTokens(100)
                .outputTokens(200)
                .modelName("llama3.2")
                .resourceId("query-abc-123")
                .build();

        when(eventRepository.save(any(AnalyticsEvent.class))).thenReturn(savedEvent);

        IngestEventResponseDto result = ingestionService.ingestEvent(request);

        assertThat(result.isDuplicate()).isFalse();

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());
        AnalyticsEvent captured = captor.getValue();
        assertThat(captured.getActorId()).isEqualTo(actorId);
        assertThat(captured.getCorrelationId()).isEqualTo(correlationId);
        assertThat(captured.getDurationMs()).isEqualTo(150L);
        assertThat(captured.getInputTokens()).isEqualTo(100);
        assertThat(captured.getOutputTokens()).isEqualTo(200);
        assertThat(captured.getModelName()).isEqualTo("llama3.2");
    }
}
