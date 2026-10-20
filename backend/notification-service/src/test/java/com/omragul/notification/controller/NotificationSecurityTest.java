package com.omragul.notification.controller;

import com.omragul.notification.config.SecurityConfig;
import com.omragul.notification.dto.response.BatchUpdateResponseDto;
import com.omragul.notification.dto.response.NotificationResponseDto;
import com.omragul.notification.dto.response.PageResponseDto;
import com.omragul.notification.dto.response.UnreadCountResponseDto;
import com.omragul.notification.enums.NotificationType;
import com.omragul.notification.exception.GlobalExceptionHandler;
import com.omragul.notification.exception.ResourceNotFoundException;
import com.omragul.notification.security.JwtAuthenticationEntryPoint;
import com.omragul.notification.security.JwtAuthenticationFilter;
import com.omragul.notification.security.JwtValidator;
import com.omragul.notification.service.NotificationService;
import com.omragul.notification.util.TestJwtFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({NotificationController.class, InternalNotificationIngestionController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtValidator.class, GlobalExceptionHandler.class})
class NotificationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @DisplayName("GET /api/notifications without token returns 401 Unauthorized")
    void testGetNotificationsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/notifications"));
    }

    @Test
    @DisplayName("GET /api/notifications with invalid token returns 401 Unauthorized")
    void testGetNotificationsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/notifications with valid user token returns 200 OK with notifications")
    void testGetNotificationsSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtFactory.generateUserToken(userId);

        NotificationResponseDto notificationDto = NotificationResponseDto.builder()
                .id(UUID.randomUUID())
                .recipientId(userId)
                .type(NotificationType.DOCUMENT_PROCESSING)
                .title("Processing Complete")
                .message("Your document processing finished")
                .read(false)
                .createdAt(Instant.now())
                .build();

        PageResponseDto<NotificationResponseDto> pageResponse = PageResponseDto.<NotificationResponseDto>builder()
                .content(List.of(notificationDto))
                .pageNumber(0)
                .pageSize(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(notificationService.getNotifications(eq(userId), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Processing Complete"))
                .andExpect(jsonPath("$.content[0].recipient_id").value(userId.toString()))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    @DisplayName("GET /api/notifications/{id} returns notification when owned by user")
    void testGetNotificationByIdSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        String token = TestJwtFactory.generateUserToken(userId);

        NotificationResponseDto notificationDto = NotificationResponseDto.builder()
                .id(notificationId)
                .recipientId(userId)
                .type(NotificationType.SYSTEM)
                .title("System Maintenance")
                .message("Scheduled update at midnight")
                .read(false)
                .createdAt(Instant.now())
                .build();

        when(notificationService.getNotificationById(notificationId, userId))
                .thenReturn(notificationDto);

        mockMvc.perform(get("/api/notifications/" + notificationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.title").value("System Maintenance"));
    }

    @Test
    @DisplayName("GET /api/notifications/{id} for another user's notification returns 404")
    void testCrossUserAccessReturnsNotFound() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID notificationB = UUID.randomUUID();
        String tokenA = TestJwtFactory.generateUserToken(userA);

        when(notificationService.getNotificationById(notificationB, userA))
                .thenThrow(new ResourceNotFoundException("Notification not found"));

        mockMvc.perform(get("/api/notifications/" + notificationB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count returns unread count")
    void testGetUnreadCount() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtFactory.generateUserToken(userId);

        when(notificationService.getUnreadCount(userId))
                .thenReturn(UnreadCountResponseDto.builder().unreadCount(7).build());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_count").value(7));
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read marks notification read")
    void testMarkAsRead() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        String token = TestJwtFactory.generateUserToken(userId);

        NotificationResponseDto notificationDto = NotificationResponseDto.builder()
                .id(notificationId)
                .recipientId(userId)
                .type(NotificationType.SECURITY)
                .title("Security Alert")
                .message("New sign-in detected")
                .read(true)
                .readAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(notificationService.markAsRead(notificationId, userId))
                .thenReturn(notificationDto);

        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @DisplayName("PATCH /api/notifications/read-all marks all unread notifications read")
    void testMarkAllAsRead() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = TestJwtFactory.generateUserToken(userId);

        when(notificationService.markAllAsRead(userId))
                .thenReturn(BatchUpdateResponseDto.builder()
                        .updatedCount(4)
                        .message("All unread notifications marked as read")
                        .build());

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated_count").value(4));
    }

    @Test
    @DisplayName("DELETE /api/notifications/{id} deletes notification and returns 204")
    void testDeleteNotification() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        String token = TestJwtFactory.generateUserToken(userId);

        mockMvc.perform(delete("/api/notifications/" + notificationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/notifications/{id} for another user's notification returns 404")
    void testDeleteNotificationNotOwned() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID notificationB = UUID.randomUUID();
        String tokenA = TestJwtFactory.generateUserToken(userA);

        doThrow(new ResourceNotFoundException("Notification not found"))
                .when(notificationService).deleteNotification(notificationB, userA);

        mockMvc.perform(delete("/api/notifications/" + notificationB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /internal/notifications is not accessible anonymously (401 or 404)")
    void testInternalIngestionNotAnonymous() throws Exception {
        mockMvc.perform(post("/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + UUID.randomUUID() + "\",\"title\":\"Test\"}"))
                .andExpect(status().isUnauthorized());
    }
}
