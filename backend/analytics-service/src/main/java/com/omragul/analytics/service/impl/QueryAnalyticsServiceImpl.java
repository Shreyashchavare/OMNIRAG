package com.omragul.analytics.service.impl;

import com.omragul.analytics.dto.response.QueryAnalyticsResponseDto;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.repository.AnalyticsEventRepository;
import com.omragul.analytics.service.QueryAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class QueryAnalyticsServiceImpl implements QueryAnalyticsService {

    private final AnalyticsEventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public QueryAnalyticsResponseDto getQueryAnalytics(Instant from, Instant to) {

        long total = eventRepository.countQueryEventsByPeriod(from, to);
        long succeeded = eventRepository.countByEventTypeAndPeriod(EventType.QUERY_SUCCEEDED, from, to);
        long failed = eventRepository.countByEventTypeAndPeriod(EventType.QUERY_FAILED, from, to);
        Double avgDurationMs = eventRepository.avgQueryDurationMs(from, to);

        String note = null;
        if (avgDurationMs == null) {
            note = "Average query duration is unavailable: no query events with durationMs have been ingested yet.";
        }

        return QueryAnalyticsResponseDto.builder()
                .generatedAt(Instant.now())
                .periodFrom(from)
                .periodTo(to)
                .totalQueryEvents(total)
                .succeeded(succeeded)
                .failed(failed)
                .avgDurationMs(avgDurationMs)
                .note(note)
                .build();
    }
}
