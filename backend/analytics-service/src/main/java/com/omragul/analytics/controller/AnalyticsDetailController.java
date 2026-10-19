package com.omragul.analytics.controller;

import com.omragul.analytics.dto.response.DocumentAnalyticsResponseDto;
import com.omragul.analytics.dto.response.QueryAnalyticsResponseDto;
import com.omragul.analytics.dto.response.UsageAnalyticsResponseDto;
import com.omragul.analytics.dto.response.UserAnalyticsResponseDto;
import com.omragul.analytics.exception.InvalidEventException;
import com.omragul.analytics.service.DocumentAnalyticsService;
import com.omragul.analytics.service.QueryAnalyticsService;
import com.omragul.analytics.service.UsageAnalyticsService;
import com.omragul.analytics.service.UserAnalyticsService;
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
 * Fine-grained analytics endpoints for documents, queries, usage, and users.
 * All endpoints require ANALYTICS_READ permission or ADMIN role.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsDetailController {

    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 365;

    private final DocumentAnalyticsService documentAnalyticsService;
    private final QueryAnalyticsService queryAnalyticsService;
    private final UsageAnalyticsService usageAnalyticsService;
    private final UserAnalyticsService userAnalyticsService;

    /**
     * GET /api/analytics/documents
     */
    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('ANALYTICS_READ') or hasRole('ADMIN')")
    public ResponseEntity<DocumentAnalyticsResponseDto> getDocumentAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Instant[] period = resolvePeriod(from, to);
        return ResponseEntity.ok(documentAnalyticsService.getDocumentAnalytics(period[0], period[1]));
    }

    /**
     * GET /api/analytics/queries
     */
    @GetMapping("/queries")
    @PreAuthorize("hasAuthority('ANALYTICS_READ') or hasRole('ADMIN')")
    public ResponseEntity<QueryAnalyticsResponseDto> getQueryAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Instant[] period = resolvePeriod(from, to);
        return ResponseEntity.ok(queryAnalyticsService.getQueryAnalytics(period[0], period[1]));
    }

    /**
     * GET /api/analytics/usage
     */
    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('ANALYTICS_READ') or hasRole('ADMIN')")
    public ResponseEntity<UsageAnalyticsResponseDto> getUsageAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Instant[] period = resolvePeriod(from, to);
        return ResponseEntity.ok(usageAnalyticsService.getUsageAnalytics(period[0], period[1]));
    }

    /**
     * GET /api/analytics/users
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ANALYTICS_READ') or hasRole('ADMIN')")
    public ResponseEntity<UserAnalyticsResponseDto> getUserAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Instant[] period = resolvePeriod(from, to);
        return ResponseEntity.ok(userAnalyticsService.getUserAnalytics(period[0], period[1]));
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
