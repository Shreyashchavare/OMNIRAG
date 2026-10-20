package com.omragul.notification.controller;

import com.omragul.notification.dto.response.BatchUpdateResponseDto;
import com.omragul.notification.dto.response.NotificationResponseDto;
import com.omragul.notification.dto.response.PageResponseDto;
import com.omragul.notification.dto.response.UnreadCountResponseDto;
import com.omragul.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for authenticated user notification operations.
 * Recipient ID is strictly derived from the authenticated JWT principal.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/notifications?page=0&size=20&unreadOnly=false
     * Fetches paginated notifications for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<PageResponseDto<NotificationResponseDto>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication);
        PageResponseDto<NotificationResponseDto> response =
                notificationService.getNotifications(userId, page, size, unreadOnly);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/notifications/{notificationId}
     * Fetches a single notification if owned by the authenticated user.
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResponseDto> getNotificationById(
            @PathVariable UUID notificationId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication);
        NotificationResponseDto response =
                notificationService.getNotificationById(notificationId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/notifications/unread-count
     * Fetches the unread notification count for the authenticated user.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponseDto> getUnreadCount(Authentication authentication) {
        UUID userId = resolveUserId(authentication);
        UnreadCountResponseDto response = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/notifications/{notificationId}/read
     * Marks a single notification owned by the authenticated user as read (idempotent).
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponseDto> markAsRead(
            @PathVariable UUID notificationId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication);
        NotificationResponseDto response = notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/notifications/read-all
     * Marks all unread notifications for the authenticated user as read.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<BatchUpdateResponseDto> markAllAsRead(Authentication authentication) {
        UUID userId = resolveUserId(authentication);
        BatchUpdateResponseDto response = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/notifications/{notificationId}
     * Deletes a notification owned by the authenticated user.
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID notificationId,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(authentication);
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Safely extracts the user UUID from the authenticated principal.
     */
    private UUID resolveUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(principal.toString());
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid authenticated principal identifier");
        }
    }
}
