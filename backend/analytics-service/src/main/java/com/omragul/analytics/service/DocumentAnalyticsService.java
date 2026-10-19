package com.omragul.analytics.service;

import com.omragul.analytics.dto.response.DocumentAnalyticsResponseDto;

import java.time.Instant;

public interface DocumentAnalyticsService {

    DocumentAnalyticsResponseDto getDocumentAnalytics(Instant from, Instant to);
}
