package com.omragul.analytics.service;

import com.omragul.analytics.dto.response.DashboardResponseDto;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.repository.AnalyticsEventRepository;
import com.omragul.analytics.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AnalyticsEventRepository eventRepository;

    private DashboardService dashboardService;

    private Instant from;
    private Instant to;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(eventRepository);
        to = Instant.now();
        from = to.minus(30, ChronoUnit.DAYS);
    }

    @Test
    void getDashboard_whenNoEvents_returnsEmptyStatusAndZeroCounts() {
        // All queries return 0
        when(eventRepository.countByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countDocumentEventsByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countQueryEventsByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countModelUsageByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countByEventTypeAndPeriod(any(EventType.class), eq(from), eq(to))).thenReturn(0L);
        when(eventRepository.avgQueryDurationMs(from, to)).thenReturn(null);
        when(eventRepository.sumInputTokensByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.sumOutputTokensByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countDistinctModelsByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countDistinctActiveUsersByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countUserRegistrationsByPeriod(from, to)).thenReturn(0L);

        DashboardResponseDto result = dashboardService.getDashboard(from, to);

        assertThat(result.getDataStatus()).isEqualTo("EMPTY");
        assertThat(result.getDocuments().getTotal()).isZero();
        assertThat(result.getQueries().getTotal()).isZero();
        assertThat(result.getUsage().getTotalTokens()).isZero();
        assertThat(result.getUsers().getActiveUsers()).isZero();
        assertThat(result.getGeneratedAt()).isNotNull();
        assertThat(result.getPeriodFrom()).isEqualTo(from);
        assertThat(result.getPeriodTo()).isEqualTo(to);
    }

    @Test
    void getDashboard_whenEventsExist_returnsAvailableStatusAndCorrectCounts() {
        when(eventRepository.countByPeriod(from, to)).thenReturn(50L);
        when(eventRepository.countDocumentEventsByPeriod(from, to)).thenReturn(20L);
        when(eventRepository.countQueryEventsByPeriod(from, to)).thenReturn(15L);
        when(eventRepository.countModelUsageByPeriod(from, to)).thenReturn(10L);
        when(eventRepository.countByEventTypeAndPeriod(EventType.DOCUMENT_PROCESSED, from, to)).thenReturn(12L);
        when(eventRepository.countByEventTypeAndPeriod(EventType.DOCUMENT_FAILED, from, to)).thenReturn(5L);
        when(eventRepository.countByEventTypeAndPeriod(EventType.DOCUMENT_DELETED, from, to)).thenReturn(3L);
        when(eventRepository.countByEventTypeAndPeriod(EventType.QUERY_SUCCEEDED, from, to)).thenReturn(13L);
        when(eventRepository.countByEventTypeAndPeriod(EventType.QUERY_FAILED, from, to)).thenReturn(2L);
        when(eventRepository.avgQueryDurationMs(from, to)).thenReturn(250.0);
        when(eventRepository.sumInputTokensByPeriod(from, to)).thenReturn(5000L);
        when(eventRepository.sumOutputTokensByPeriod(from, to)).thenReturn(3000L);
        when(eventRepository.countDistinctModelsByPeriod(from, to)).thenReturn(2L);
        when(eventRepository.countDistinctActiveUsersByPeriod(from, to)).thenReturn(8L);
        when(eventRepository.countUserRegistrationsByPeriod(from, to)).thenReturn(3L);

        DashboardResponseDto result = dashboardService.getDashboard(from, to);

        assertThat(result.getDataStatus()).isEqualTo("AVAILABLE");
        assertThat(result.getDocuments().getTotal()).isEqualTo(20L);
        assertThat(result.getDocuments().getProcessed()).isEqualTo(12L);
        assertThat(result.getDocuments().getFailed()).isEqualTo(5L);
        assertThat(result.getQueries().getTotal()).isEqualTo(15L);
        assertThat(result.getQueries().getSucceeded()).isEqualTo(13L);
        assertThat(result.getQueries().getAvgDurationMs()).isEqualTo(250.0);
        assertThat(result.getUsage().getTotalInputTokens()).isEqualTo(5000L);
        assertThat(result.getUsage().getTotalOutputTokens()).isEqualTo(3000L);
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(8000L);
        assertThat(result.getUsers().getActiveUsers()).isEqualTo(8L);
        assertThat(result.getUsers().getNewRegistrations()).isEqualTo(3L);
    }

    @Test
    void getDashboard_tokenSumDoesNotOverflow_withLargeCounts() {
        long largeInput = Long.MAX_VALUE / 2;
        long largeOutput = Long.MAX_VALUE / 2;

        when(eventRepository.countByPeriod(from, to)).thenReturn(1L);
        when(eventRepository.countDocumentEventsByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countQueryEventsByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countModelUsageByPeriod(from, to)).thenReturn(1L);
        when(eventRepository.countByEventTypeAndPeriod(any(EventType.class), eq(from), eq(to))).thenReturn(0L);
        when(eventRepository.avgQueryDurationMs(from, to)).thenReturn(null);
        when(eventRepository.sumInputTokensByPeriod(from, to)).thenReturn(largeInput);
        when(eventRepository.sumOutputTokensByPeriod(from, to)).thenReturn(largeOutput);
        when(eventRepository.countDistinctModelsByPeriod(from, to)).thenReturn(1L);
        when(eventRepository.countDistinctActiveUsersByPeriod(from, to)).thenReturn(0L);
        when(eventRepository.countUserRegistrationsByPeriod(from, to)).thenReturn(0L);

        DashboardResponseDto result = dashboardService.getDashboard(from, to);

        // Should not throw; total is calculated within long range
        assertThat(result.getUsage().getTotalInputTokens()).isEqualTo(largeInput);
        assertThat(result.getUsage().getTotalOutputTokens()).isEqualTo(largeOutput);
    }
}
