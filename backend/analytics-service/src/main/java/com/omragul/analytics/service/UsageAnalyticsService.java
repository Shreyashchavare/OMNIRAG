package com.omragul.analytics.service;

import com.omragul.analytics.dto.response.UsageAnalyticsResponseDto;

import java.time.Instant;

public interface UsageAnalyticsService {

    UsageAnalyticsResponseDto getUsageAnalytics(Instant from, Instant to);
}
