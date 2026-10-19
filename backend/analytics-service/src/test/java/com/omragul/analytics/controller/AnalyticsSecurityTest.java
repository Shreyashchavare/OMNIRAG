package com.omragul.analytics.controller;

import com.omragul.analytics.config.SecurityConfig;
import com.omragul.analytics.dto.request.IngestEventRequestDto;
import com.omragul.analytics.dto.response.DashboardResponseDto;
import com.omragul.analytics.dto.response.IngestEventResponseDto;
import com.omragul.analytics.enums.EventStatus;
import com.omragul.analytics.enums.EventType;
import com.omragul.analytics.enums.SourceService;
import com.omragul.analytics.security.JwtAuthenticationEntryPoint;
import com.omragul.analytics.security.JwtAuthenticationFilter;
import com.omragul.analytics.security.JwtValidator;
import com.omragul.analytics.service.DashboardService;
import com.omragul.analytics.service.DocumentAnalyticsService;
import com.omragul.analytics.service.EventIngestionService;
import com.omragul.analytics.service.QueryAnalyticsService;
import com.omragul.analytics.service.UsageAnalyticsService;
import com.omragul.analytics.service.UserAnalyticsService;
import com.omragul.analytics.util.TestJwtFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-focused controller tests.
 * Verifies that unauthenticated and unauthorized requests are rejected,
 * and valid JWT-authenticated requests succeed.
 */
@WebMvcTest({AnalyticsController.class, AnalyticsDetailController.class, InternalEventIngestionController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtValidator.class})
class AnalyticsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private DocumentAnalyticsService documentAnalyticsService;

    @MockitoBean
    private QueryAnalyticsService queryAnalyticsService;

    @MockitoBean
    private UsageAnalyticsService usageAnalyticsService;

    @MockitoBean
    private UserAnalyticsService userAnalyticsService;

    @MockitoBean
    private EventIngestionService eventIngestionService;

    @Test
    void dashboard_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/analytics/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboard_withUserToken_returns403() throws Exception {
        String token = TestJwtFactory.generateUserToken(UUID.randomUUID());

        mockMvc.perform(get("/api/analytics/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_withAdminToken_returns200() throws Exception {
        String token = TestJwtFactory.generateAdminToken(UUID.randomUUID());

        when(dashboardService.getDashboard(any(), any()))
                .thenReturn(DashboardResponseDto.builder()
                        .dataStatus("AVAILABLE")
                        .periodFrom(Instant.now().minusSeconds(3600))
                        .periodTo(Instant.now())
                        .build());

        mockMvc.perform(get("/api/analytics/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void documents_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/analytics/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void queries_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/analytics/queries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usage_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/analytics/usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void users_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/analytics/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEvents_withoutToken_returns401() throws Exception {
        IngestEventRequestDto request = IngestEventRequestDto.builder()
                .eventId(UUID.randomUUID())
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .status(EventStatus.SUCCESS)
                .build();

        mockMvc.perform(post("/internal/analytics/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEvents_withUserToken_returns403() throws Exception {
        String token = TestJwtFactory.generateUserToken(UUID.randomUUID());
        IngestEventRequestDto request = IngestEventRequestDto.builder()
                .eventId(UUID.randomUUID())
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .status(EventStatus.SUCCESS)
                .build();

        mockMvc.perform(post("/internal/analytics/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalEvents_withInternalToken_returns201() throws Exception {
        UUID eventId = UUID.randomUUID();
        String token = TestJwtFactory.generateInternalToken(UUID.randomUUID());
        IngestEventRequestDto request = IngestEventRequestDto.builder()
                .eventId(eventId)
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .status(EventStatus.SUCCESS)
                .build();

        when(eventIngestionService.ingestEvent(any()))
                .thenReturn(IngestEventResponseDto.builder()
                        .id(1L)
                        .eventId(eventId)
                        .receivedAt(Instant.now())
                        .duplicate(false)
                        .build());

        mockMvc.perform(post("/internal/analytics/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void internalEvents_withAdminToken_returns201() throws Exception {
        UUID eventId = UUID.randomUUID();
        String token = TestJwtFactory.generateAdminToken(UUID.randomUUID());
        IngestEventRequestDto request = IngestEventRequestDto.builder()
                .eventId(eventId)
                .eventType(EventType.DOCUMENT_UPLOADED)
                .sourceService(SourceService.KNOWLEDGE_SERVICE)
                .occurredAt(Instant.now().minusSeconds(5))
                .status(EventStatus.SUCCESS)
                .build();

        when(eventIngestionService.ingestEvent(any()))
                .thenReturn(IngestEventResponseDto.builder()
                        .id(1L)
                        .eventId(eventId)
                        .receivedAt(Instant.now())
                        .duplicate(false)
                        .build());

        mockMvc.perform(post("/internal/analytics/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false));
    }
}
