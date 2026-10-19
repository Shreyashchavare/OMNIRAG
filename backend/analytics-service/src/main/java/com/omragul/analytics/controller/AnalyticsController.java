package com.omragul.analytics.controller;

import com.omragul.analytics.dto.response.DashboardResponseDto;
import com.omragul.analytics.exception.InvalidEventException;
import com.omragul.analytics.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Analytics dashboard endpoint.
 * Requires: ANALYTICS_READ permission (granted to ADMIN role in Identity Service V9 seed).
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 365;

    private final DashboardService dashboardService;

    /**
     * GET /api/analytics/dashboard
     * <p>
     * Query params (both optional):
     * <ul>
     *   <li>{@code from} — ISO-8601 UTC timestamp (e.g. 2026-10-01T00:00:00Z)</li>
     *   <li>{@code to}   — ISO-8601 UTC timestamp</li>
     * </ul>
     * Defaults to the last 30 days when omitted.
     * </p>
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('ANALYTICS_READ') or hasRole('ADMIN')")
    public ResponseEntity<DashboardResponseDto> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Instant[] period = resolvePeriod(from, to);
        DashboardResponseDto response = dashboardService.getDashboard(period[0], period[1]);
        return ResponseEntity.ok(response);
    }

    private Instant[] resolvePeriod(Instant from, Instant to) {
        Instant now = Instant.now();
        Instant resolvedTo = (to != null) ? to : now;
        Instant resolvedFrom = (from != null) ? from : resolvedTo.minus(DEFAULT_PERIOD_DAYS, ChronoUnit.DAYS);

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new InvalidEventException("'from' must not be after 'to'");
        }

        long rangeDays = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo);
        if (rangeDays > MAX_RANGE_DAYS) {
            throw new InvalidEventException(
                    "Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }

        return new Instant[]{ resolvedFrom, resolvedTo };
    }
}
