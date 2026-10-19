package com.omragul.analytics.service;

import com.omragul.analytics.dto.response.UserAnalyticsResponseDto;

import java.time.Instant;

public interface UserAnalyticsService {

    UserAnalyticsResponseDto getUserAnalytics(Instant from, Instant to);
}
