package com.omragul.notification.service;

import com.omragul.notification.config.NotificationProperties;
import com.omragul.notification.dto.request.IngestNotificationRequestDto;
import com.omragul.notification.dto.response.BatchUpdateResponseDto;
import com.omragul.notification.dto.response.NotificationResponseDto;
import com.omragul.notification.dto.response.PageResponseDto;
import com.omragul.notification.dto.response.UnreadCountResponseDto;
import com.omragul.notification.entity.Notification;
import com.omragul.notification.enums.NotificationType;
import com.omragul.notification.exception.ResourceNotFoundException;
import com.omragul.notification.repository.NotificationRepository;
import com.omragul.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Spy
    private NotificationProperties notificationProperties = new NotificationProperties();

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private UUID userId;
    private UUID otherUserId;
    private UUID notificationId;
    private Notification sampleNotification;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        notificationId = UUID.randomUUID();

        sampleNotification = Notification.builder()
                .id(notificationId)
                .recipientId(userId)
                .type(NotificationType.DOCUMENT_PROCESSING)
                .title("Document Processed")
                .message("Your document was processed successfully")
                .metadata(Map.of("doc_id", "123"))
                .sourceService("PROCESSING_SERVICE")
                .sourceEventType("DOCUMENT_INDEXED")
                .sourceEventId("evt-001")
                .read(false)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("getNotifications returns paginated notifications for recipient")
    void testGetNotifications() {
        Page<Notification> page = new PageImpl<>(List.of(sampleNotification));
        when(notificationRepository.findByRecipientId(eq(userId), any(Pageable.class))).thenReturn(page);

        PageResponseDto<NotificationResponseDto> response =
                notificationService.getNotifications(userId, 0, 10, false);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("Document Processed");
        assertThat(response.getContent().get(0).getRecipientId()).isEqualTo(userId);
        verify(notificationRepository).findByRecipientId(eq(userId), any(Pageable.class));
    }

    @Test
    @DisplayName("getNotifications with unreadOnly=true queries unread notifications")
    void testGetNotificationsUnreadOnly() {
        Page<Notification> page = new PageImpl<>(List.of(sampleNotification));
        when(notificationRepository.findByRecipientIdAndRead(eq(userId), eq(false), any(Pageable.class)))
                .thenReturn(page);

        PageResponseDto<NotificationResponseDto> response =
                notificationService.getNotifications(userId, 0, 10, true);

        assertThat(response.getContent()).hasSize(1);
        verify(notificationRepository).findByRecipientIdAndRead(eq(userId), eq(false), any(Pageable.class));
    }

    @Test
    @DisplayName("getNotificationById returns notification when owned by user")
    void testGetNotificationByIdSuccess() {
        when(notificationRepository.findByIdAndRecipientId(notificationId, userId))
                .thenReturn(Optional.of(sampleNotification));

        NotificationResponseDto response = notificationService.getNotificationById(notificationId, userId);

        assertThat(response.getId()).isEqualTo(notificationId);
        assertThat(response.getTitle()).isEqualTo("Document Processed");
    }

    @Test
    @DisplayName("getNotificationById throws ResourceNotFoundException when not owned by user")
    void testGetNotificationByIdNotOwned() {
        when(notificationRepository.findByIdAndRecipientId(notificationId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getNotificationById(notificationId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Notification not found");
    }

    @Test
    @DisplayName("getUnreadCount returns correct unread count")
    void testGetUnreadCount() {
        when(notificationRepository.countByRecipientIdAndReadFalse(userId)).thenReturn(5L);

        UnreadCountResponseDto response = notificationService.getUnreadCount(userId);

        assertThat(response.getUnreadCount()).isEqualTo(5L);
        verify(notificationRepository).countByRecipientIdAndReadFalse(userId);
    }

    @Test
    @DisplayName("markAsRead marks unread notification as read")
    void testMarkAsRead() {
        when(notificationRepository.findByIdAndRecipientId(notificationId, userId))
                .thenReturn(Optional.of(sampleNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDto response = notificationService.markAsRead(notificationId, userId);

        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isNotNull();
        verify(notificationRepository).save(sampleNotification);
    }

    @Test
    @DisplayName("markAsRead is idempotent when notification is already read")
    void testMarkAsReadIdempotent() {
        Instant originalReadAt = Instant.now().minusSeconds(60);
        sampleNotification.setRead(true);
        sampleNotification.setReadAt(originalReadAt);

        when(notificationRepository.findByIdAndRecipientId(notificationId, userId))
                .thenReturn(Optional.of(sampleNotification));

        NotificationResponseDto response = notificationService.markAsRead(notificationId, userId);

        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isEqualTo(originalReadAt);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("markAllAsRead marks all unread notifications for recipient")
    void testMarkAllAsRead() {
        when(notificationRepository.markAllAsRead(eq(userId), any(Instant.class))).thenReturn(3);

        BatchUpdateResponseDto response = notificationService.markAllAsRead(userId);

        assertThat(response.getUpdatedCount()).isEqualTo(3L);
        assertThat(response.getMessage()).contains("marked as read");
        verify(notificationRepository).markAllAsRead(eq(userId), any(Instant.class));
    }

    @Test
    @DisplayName("deleteNotification deletes owned notification")
    void testDeleteNotificationSuccess() {
        when(notificationRepository.existsByIdAndRecipientId(notificationId, userId)).thenReturn(true);

        notificationService.deleteNotification(notificationId, userId);

        verify(notificationRepository).deleteByIdAndRecipientId(notificationId, userId);
    }

    @Test
    @DisplayName("deleteNotification throws ResourceNotFoundException when notification not owned")
    void testDeleteNotificationNotFound() {
        when(notificationRepository.existsByIdAndRecipientId(notificationId, otherUserId)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.deleteNotification(notificationId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Notification not found");

        verify(notificationRepository, never()).deleteByIdAndRecipientId(any(), any());
    }

    @Test
    @DisplayName("ingestNotification saves new notification")
    void testIngestNotification() {
        IngestNotificationRequestDto request = IngestNotificationRequestDto.builder()
                .recipientId(userId)
                .type(NotificationType.SECURITY)
                .title("Password Changed")
                .message("Your account password was updated")
                .sourceService("IDENTITY_SERVICE")
                .sourceEventId("pwd-change-001")
                .build();

        when(notificationRepository.findBySourceServiceAndSourceEventId("IDENTITY_SERVICE", "pwd-change-001"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        NotificationResponseDto response = notificationService.ingestNotification(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Password Changed");
        assertThat(response.isRead()).isFalse();
    }

    @Test
    @DisplayName("ingestNotification is idempotent for same sourceService + sourceEventId")
    void testIngestNotificationDuplicate() {
        IngestNotificationRequestDto request = IngestNotificationRequestDto.builder()
                .recipientId(userId)
                .type(NotificationType.DOCUMENT_PROCESSING)
                .title("Document Processed")
                .message("Your document was processed")
                .sourceService("PROCESSING_SERVICE")
                .sourceEventId("evt-001")
                .build();

        when(notificationRepository.findBySourceServiceAndSourceEventId("PROCESSING_SERVICE", "evt-001"))
                .thenReturn(Optional.of(sampleNotification));

        NotificationResponseDto response = notificationService.ingestNotification(request);

        assertThat(response.getId()).isEqualTo(notificationId);
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
