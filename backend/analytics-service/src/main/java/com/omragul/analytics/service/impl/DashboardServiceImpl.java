package com.omragul.analytics.service.impl;

import com.omragul.analytics.dto.response.DashboardResponseDto;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.repository.AnalyticsEventRepository;
import com.omragul.analytics.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AnalyticsEventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponseDto getDashboard(Instant from, Instant to) {

        long totalEvents = eventRepository.countByPeriod(from, to);

        // Document metrics
        long documentTotal = eventRepository.countDocumentEventsByPeriod(from, to);
        long documentProcessed = eventRepository.countByEventTypeAndPeriod(
                EventType.DOCUMENT_PROCESSED, from, to);
        long documentFailed = eventRepository.countByEventTypeAndPeriod(
                EventType.DOCUMENT_FAILED, from, to);
        long documentDeleted = eventRepository.countByEventTypeAndPeriod(
                EventType.DOCUMENT_DELETED, from, to);

        // Query metrics
        long queryTotal = eventRepository.countQueryEventsByPeriod(from, to);
        long querySucceeded = eventRepository.countByEventTypeAndPeriod(
                EventType.QUERY_SUCCEEDED, from, to);
        long queryFailed = eventRepository.countByEventTypeAndPeriod(
                EventType.QUERY_FAILED, from, to);
        Double avgDurationMs = eventRepository.avgQueryDurationMs(from, to);

        // Usage metrics
        long modelRequests = eventRepository.countModelUsageByPeriod(from, to);
        long inputTokens = eventRepository.sumInputTokensByPeriod(from, to);
        long outputTokens = eventRepository.sumOutputTokensByPeriod(from, to);
        long distinctModels = eventRepository.countDistinctModelsByPeriod(from, to);

        // User metrics
        long activeUsers = eventRepository.countDistinctActiveUsersByPeriod(from, to);
        long newRegistrations = eventRepository.countUserRegistrationsByPeriod(from, to);

        String dataStatus = totalEvents == 0 ? "EMPTY" : "AVAILABLE";

        return DashboardResponseDto.builder()
                .generatedAt(Instant.now())
                .dataStatus(dataStatus)
                .periodFrom(from)
                .periodTo(to)
                .documents(DashboardResponseDto.DocumentSummary.builder()
                        .total(documentTotal)
                        .processed(documentProcessed)
                        .failed(documentFailed)
                        .deleted(documentDeleted)
                        .build())
                .queries(DashboardResponseDto.QuerySummary.builder()
                        .total(queryTotal)
                        .succeeded(querySucceeded)
                        .failed(queryFailed)
                        .avgDurationMs(avgDurationMs)
                        .build())
                .usage(DashboardResponseDto.UsageSummary.builder()
                        .modelUsageRequests(modelRequests)
                        .totalInputTokens(inputTokens)
                        .totalOutputTokens(outputTokens)
                        .totalTokens(inputTokens + outputTokens)
                        .distinctModels(distinctModels)
                        .build())
                .users(DashboardResponseDto.UserSummary.builder()
                        .activeUsers(activeUsers)
                        .newRegistrations(newRegistrations)
                        .note("Active users reflects distinct actor IDs in analytics events for the period, " +
                              "not total user count from Identity Service (that integration is pending).")
                        .build())
                .build();
    }
}
