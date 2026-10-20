package com.omragul.notification.controller;

import com.omragul.notification.dto.request.IngestNotificationRequestDto;
import com.omragul.notification.dto.response.NotificationResponseDto;
import com.omragul.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal notification ingestion endpoint.
 * <p>
 * DEFERRED ARCHITECTURAL BOUNDARY:
 * This controller is unregistered and disabled by default (notification.internal-ingestion.enabled=false).
 * When enabled, it strictly requires RS256 JWT Bearer token authentication with ROLE_INTERNAL or ROLE_ADMIN.
 * No internal API keys, shared static secrets, or anonymous endpoints are used or permitted.
 * </p>
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.internal-ingestion.enabled", havingValue = "true", matchIfMissing = false)
public class InternalNotificationIngestionController {

    private final NotificationService notificationService;

    /**
     * POST /internal/notifications
     * Ingests a new notification. Idempotent based on sourceService + sourceEventId.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('INTERNAL', 'ADMIN')")
    public ResponseEntity<NotificationResponseDto> ingestNotification(
            @Valid @RequestBody IngestNotificationRequestDto request
    ) {
        NotificationResponseDto response = notificationService.ingestNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
