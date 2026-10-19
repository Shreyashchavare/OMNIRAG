package com.omragul.analytics.service.impl;

import com.omragul.analytics.dto.response.DocumentAnalyticsResponseDto;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.repository.AnalyticsEventRepository;
import com.omragul.analytics.service.DocumentAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DocumentAnalyticsServiceImpl implements DocumentAnalyticsService {

    private final AnalyticsEventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public DocumentAnalyticsResponseDto getDocumentAnalytics(Instant from, Instant to) {

        long total = eventRepository.countDocumentEventsByPeriod(from, to);
        long uploaded = eventRepository.countByEventTypeAndPeriod(EventType.DOCUMENT_UPLOADED, from, to);
        long processed = eventRepository.countByEventTypeAndPeriod(EventType.DOCUMENT_PROCESSED, from, to);
        long failed = eventRepository.countByEventTypeAndPeriod(EventType.DOCUMENT_FAILED, from, to);
        long deleted = eventRepository.countByEventTypeAndPeriod(EventType.DOCUMENT_DELETED, from, to);

        return DocumentAnalyticsResponseDto.builder()
                .generatedAt(Instant.now())
                .periodFrom(from)
                .periodTo(to)
                .totalDocumentEvents(total)
                .uploaded(uploaded)
                .processed(processed)
                .failed(failed)
                .deleted(deleted)
                .build();
    }
}
