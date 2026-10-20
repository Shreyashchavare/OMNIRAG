package com.omragul.notification.service.impl;

import com.omragul.notification.config.NotificationProperties;
import com.omragul.notification.dto.request.IngestNotificationRequestDto;
import com.omragul.notification.dto.response.BatchUpdateResponseDto;
import com.omragul.notification.dto.response.NotificationResponseDto;
import com.omragul.notification.dto.response.PageResponseDto;
import com.omragul.notification.dto.response.UnreadCountResponseDto;
import com.omragul.notification.entity.Notification;
import com.omragul.notification.exception.ResourceNotFoundException;
import com.omragul.notification.repository.NotificationRepository;
import com.omragul.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link NotificationService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationProperties notificationProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<NotificationResponseDto> getNotifications(
            UUID recipientId,
            int page,
            int size,
            boolean unreadOnly
    ) {
        int boundedPage = Math.max(0, page);
        int defaultSize = notificationProperties.getPagination().getDefaultPageSize();
        int maxSize = notificationProperties.getPagination().getMaxPageSize();
        int boundedSize = size <= 0 ? defaultSize : Math.min(size, maxSize);

        // Sort newest first with deterministic tie-breaking on id
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, sort);

        Page<Notification> result = unreadOnly
                ? notificationRepository.findByRecipientIdAndRead(recipientId, false, pageable)
                : notificationRepository.findByRecipientId(recipientId, pageable);

        return PageResponseDto.of(result, NotificationResponseDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponseDto getNotificationById(UUID notificationId, UUID recipientId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        return NotificationResponseDto.fromEntity(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponseDto getUnreadCount(UUID recipientId) {
        long count = notificationRepository.countByRecipientIdAndReadFalse(recipientId);
        return UnreadCountResponseDto.builder()
                .unreadCount(count)
                .build();
    }

    @Override
    @Transactional
    public NotificationResponseDto markAsRead(UUID notificationId, UUID recipientId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        // Idempotent: only update if currently unread
        if (!notification.isRead()) {
            notification.markAsRead(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return NotificationResponseDto.fromEntity(notification);
    }

    @Override
    @Transactional
    public BatchUpdateResponseDto markAllAsRead(UUID recipientId) {
        int updated = notificationRepository.markAllAsRead(recipientId, Instant.now());
        log.info("Marked {} unread notifications as read for recipient {}", updated, recipientId);

        return BatchUpdateResponseDto.builder()
                .updatedCount(updated)
                .message("All unread notifications marked as read")
                .build();
    }

    @Override
    @Transactional
    public void deleteNotification(UUID notificationId, UUID recipientId) {
        if (!notificationRepository.existsByIdAndRecipientId(notificationId, recipientId)) {
            throw new ResourceNotFoundException("Notification not found");
        }
        notificationRepository.deleteByIdAndRecipientId(notificationId, recipientId);
    }

    @Override
    @Transactional
    public NotificationResponseDto ingestNotification(IngestNotificationRequestDto request) {
        // Idempotency check: if sourceService and sourceEventId are supplied, return existing if found
        if (request.getSourceService() != null && request.getSourceEventId() != null) {
            Optional<Notification> existing = notificationRepository
                    .findBySourceServiceAndSourceEventId(request.getSourceService(), request.getSourceEventId());
            if (existing.isPresent()) {
                log.info("Idempotent hit for notification sourceService={}, sourceEventId={}",
                        request.getSourceService(), request.getSourceEventId());
                return NotificationResponseDto.fromEntity(existing.get());
            }
        }

        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .metadata(request.getMetadata())
                .sourceService(request.getSourceService())
                .sourceEventType(request.getSourceEventType())
                .sourceEventId(request.getSourceEventId())
                .read(false)
                .expiresAt(request.getExpiresAt())
                .build();

        Notification saved = notificationRepository.save(notification);
        return NotificationResponseDto.fromEntity(saved);
    }
}
