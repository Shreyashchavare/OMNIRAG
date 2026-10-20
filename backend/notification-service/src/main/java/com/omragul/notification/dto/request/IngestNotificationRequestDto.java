package com.omragul.notification.dto.request;

import com.omragul.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Request payload for creating / ingesting a notification.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IngestNotificationRequestDto {

    @NotNull(message = "Recipient ID is required")
    private UUID recipientId;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    private Map<String, Object> metadata;

    @Size(max = 64, message = "Source service must not exceed 64 characters")
    private String sourceService;

    @Size(max = 64, message = "Source event type must not exceed 64 characters")
    private String sourceEventType;

    @Size(max = 255, message = "Source event ID must not exceed 255 characters")
    private String sourceEventId;

    private Instant expiresAt;
}
