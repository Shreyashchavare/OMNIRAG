package com.omragul.analytics.service;

import com.omragul.analytics.dto.response.DashboardResponseDto;

import java.time.Instant;

public interface DashboardService {

    /**
     * Returns a compact dashboard summary for the specified period.
     *
     * @param from inclusive start (UTC)
     * @param to   inclusive end (UTC); must not be before {@code from}
     */
    DashboardResponseDto getDashboard(Instant from, Instant to);
}
