package com.omragul.notification.repository;

import com.omragul.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Notification}.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Find paginated notifications for a recipient.
     */
    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    /**
     * Find paginated notifications for a recipient filtered by read state.
     */
    Page<Notification> findByRecipientIdAndRead(UUID recipientId, boolean read, Pageable pageable);

    /**
     * Find a notification by ID and recipient for ownership-safe single lookups.
     */
    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    /**
     * Count unread notifications for a recipient.
     */
    long countByRecipientIdAndReadFalse(UUID recipientId);

    /**
     * Marks all unread notifications for a recipient as read in a single query.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.recipientId = :recipientId AND n.read = false")
    int markAllAsRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);

    /**
     * Delete a notification owned by a recipient.
     */
    void deleteByIdAndRecipientId(UUID id, UUID recipientId);

    /**
     * Checks if a notification exists and belongs to the given recipient.
     */
    boolean existsByIdAndRecipientId(UUID id, UUID recipientId);

    /**
     * Lookup an existing notification by source service and event ID for idempotent ingestion.
     */
    Optional<Notification> findBySourceServiceAndSourceEventId(String sourceService, String sourceEventId);
}
