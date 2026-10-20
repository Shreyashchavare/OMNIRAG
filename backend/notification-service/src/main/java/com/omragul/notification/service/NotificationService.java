package com.omragul.notification.service;

import com.omragul.notification.dto.request.IngestNotificationRequestDto;
import com.omragul.notification.dto.response.BatchUpdateResponseDto;
import com.omragul.notification.dto.response.NotificationResponseDto;
import com.omragul.notification.dto.response.PageResponseDto;
import com.omragul.notification.dto.response.UnreadCountResponseDto;

import java.util.UUID;

/**
 * Service interface for notification operations.
 */
public interface NotificationService {

    /**
     * Retrieves paginated notifications for the authenticated user.
     */
    PageResponseDto<NotificationResponseDto> getNotifications(
            UUID recipientId,
            int page,
            int size,
            boolean unreadOnly
    );

    /**
     * Retrieves a single notification owned by the authenticated user.
     */
    NotificationResponseDto getNotificationById(UUID notificationId, UUID recipientId);

    /**
     * Returns the unread notification count for the authenticated user.
     */
    UnreadCountResponseDto getUnreadCount(UUID recipientId);

    /**
     * Marks a single notification as read if owned by the authenticated user (idempotent).
     */
    NotificationResponseDto markAsRead(UUID notificationId, UUID recipientId);

    /**
     * Marks all unread notifications for the authenticated user as read in a single transaction.
     */
    BatchUpdateResponseDto markAllAsRead(UUID recipientId);

    /**
     * Deletes a single notification owned by the authenticated user.
     */
    void deleteNotification(UUID notificationId, UUID recipientId);

    /**
     * Ingests a new notification with idempotency support.
     */
    NotificationResponseDto ingestNotification(IngestNotificationRequestDto request);
}
