package com.omragul.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.omragul.notification.entity.Notification;
import com.omragul.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * API response representing a single in-app notification.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponseDto {

    private UUID id;

    private UUID recipientId;

    private NotificationType type;

    private String title;

    private String message;

    private Map<String, Object> metadata;

    private String sourceService;

    private String sourceEventType;

    private String sourceEventId;

    @JsonProperty("read")
    private boolean read;

    private Instant readAt;

    private Instant createdAt;

    private Instant expiresAt;

    /**
     * Maps a Notification entity to response DTO.
     */
    public static NotificationResponseDto fromEntity(Notification entity) {
        if (entity == null) {
            return null;
        }
        return NotificationResponseDto.builder()
                .id(entity.getId())
                .recipientId(entity.getRecipientId())
                .type(entity.getType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .metadata(entity.getMetadata())
                .sourceService(entity.getSourceService())
                .sourceEventType(entity.getSourceEventType())
                .sourceEventId(entity.getSourceEventId())
                .read(entity.isRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }
}
