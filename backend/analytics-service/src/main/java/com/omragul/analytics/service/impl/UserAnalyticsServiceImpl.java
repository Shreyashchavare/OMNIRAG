package com.omragul.analytics.service.impl;

import com.omragul.analytics.dto.response.UserAnalyticsResponseDto;
import com.omragul.analytics.repository.AnalyticsEventRepository;
import com.omragul.analytics.service.UserAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserAnalyticsServiceImpl implements UserAnalyticsService {

    private final AnalyticsEventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public UserAnalyticsResponseDto getUserAnalytics(Instant from, Instant to) {

        long activeUsers = eventRepository.countDistinctActiveUsersByPeriod(from, to);
        long newRegistrations = eventRepository.countUserRegistrationsByPeriod(from, to);

        return UserAnalyticsResponseDto.builder()
                .generatedAt(Instant.now())
                .periodFrom(from)
                .periodTo(to)
                .activeUsers(activeUsers)
                .newRegistrations(newRegistrations)
                .note("'activeUsers' = distinct actorIds in analytics events for the period. " +
                      "Total registered user count from Identity Service is not available here " +
                      "(Identity integration is pending — see README for required contract).")
                .build();
    }
}
