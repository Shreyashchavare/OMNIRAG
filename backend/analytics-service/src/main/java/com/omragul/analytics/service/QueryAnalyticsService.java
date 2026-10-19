package com.omragul.analytics.service;

import com.omragul.analytics.dto.response.QueryAnalyticsResponseDto;

import java.time.Instant;

public interface QueryAnalyticsService {

    QueryAnalyticsResponseDto getQueryAnalytics(Instant from, Instant to);
}
