package com.omragul.analytics.service.impl;

import com.omragul.analytics.dto.response.UsageAnalyticsResponseDto;
import com.omragul.analytics.repository.AnalyticsEventRepository;
import com.omragul.analytics.service.UsageAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UsageAnalyticsServiceImpl implements UsageAnalyticsService {

    private final AnalyticsEventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public UsageAnalyticsResponseDto getUsageAnalytics(Instant from, Instant to) {

        long requests = eventRepository.countModelUsageByPeriod(from, to);
        long inputTokens = eventRepository.sumInputTokensByPeriod(from, to);
        long outputTokens = eventRepository.sumOutputTokensByPeriod(from, to);
        long distinctModels = eventRepository.countDistinctModelsByPeriod(from, to);

        String note = null;
        if (requests == 0) {
            note = "No MODEL_USAGE events have been ingested yet. " +
                   "Token counts will populate once producer services (RAG, Agent, Multimodal) " +
                   "begin publishing MODEL_USAGE events to the internal ingestion endpoint.";
        }

        return UsageAnalyticsResponseDto.builder()
                .generatedAt(Instant.now())
                .periodFrom(from)
                .periodTo(to)
                .modelUsageRequests(requests)
                .totalInputTokens(inputTokens)
                .totalOutputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .distinctModels(distinctModels)
                .note(note)
                .build();
    }
}
