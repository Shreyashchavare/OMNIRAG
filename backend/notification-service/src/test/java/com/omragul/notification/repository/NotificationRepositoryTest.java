package com.omragul.notification.repository;

import com.omragul.notification.config.JpaConfig;
import com.omragul.notification.entity.Notification;
import com.omragul.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
    }

    @Test
    @DisplayName("Persist notification and verify retrieval by ID and recipient")
    void testSaveAndFindByIdAndRecipient() {
        Notification notification = Notification.builder()
                .recipientId(userA)
                .type(NotificationType.DOCUMENT_PROCESSING)
                .title("Document Ready")
                .message("Your document was parsed successfully")
                .metadata(Map.of("doc_id", "doc-123"))
                .sourceService("PROCESSING_SERVICE")
                .sourceEventType("DOCUMENT_PARSED")
                .sourceEventId("evt-001")
                .read(false)
                .build();

        Notification saved = repository.save(notification);
        assertThat(saved.getId()).isNotNull();

        Optional<Notification> found = repository.findByIdAndRecipientId(saved.getId(), userA);
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Document Ready");

        // User B cannot find User A's notification
        Optional<Notification> notFoundForUserB = repository.findByIdAndRecipientId(saved.getId(), userB);
        assertThat(notFoundForUserB).isEmpty();
    }

    @Test
    @DisplayName("Pagination and unread-only filtering")
    void testPaginationAndUnreadFilter() {
        // Create 3 notifications for userA: 2 unread, 1 read
        repository.save(Notification.builder()
                .recipientId(userA)
                .type(NotificationType.SYSTEM)
                .title("Notice 1")
                .message("Message 1")
                .read(false)
                .build());

        repository.save(Notification.builder()
                .recipientId(userA)
                .type(NotificationType.SYSTEM)
                .title("Notice 2")
                .message("Message 2")
                .read(true)
                .readAt(Instant.now())
                .build());

        repository.save(Notification.builder()
                .recipientId(userA)
                .type(NotificationType.SECURITY)
                .title("Notice 3")
                .message("Message 3")
                .read(false)
                .build());

        // Also create 1 notification for userB
        repository.save(Notification.builder()
                .recipientId(userB)
                .type(NotificationType.SECURITY)
                .title("User B Notice")
                .message("Private notice")
                .read(false)
                .build());

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        // All userA notifications
        Page<Notification> allUserA = repository.findByRecipientId(userA, pageRequest);
        assertThat(allUserA.getTotalElements()).isEqualTo(3);

        // Unread only for userA
        Page<Notification> unreadUserA = repository.findByRecipientIdAndRead(userA, false, pageRequest);
        assertThat(unreadUserA.getTotalElements()).isEqualTo(2);

        // Unread count
        long unreadCount = repository.countByRecipientIdAndReadFalse(userA);
        assertThat(unreadCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("markAllAsRead updates all unread notifications for recipient")
    void testMarkAllAsRead() {
        repository.save(Notification.builder()
                .recipientId(userA)
                .type(NotificationType.SYSTEM)
                .title("N1")
                .message("M1")
                .read(false)
                .build());

        repository.save(Notification.builder()
                .recipientId(userA)
                .type(NotificationType.SYSTEM)
                .title("N2")
                .message("M2")
                .read(false)
                .build());

        // Already read
        repository.save(Notification.builder()
                .recipientId(userA)
                .type(NotificationType.SYSTEM)
                .title("N3")
                .message("M3")
                .read(true)
                .readAt(Instant.now().minusSeconds(100))
                .build());

        int updated = repository.markAllAsRead(userA, Instant.now());
        assertThat(updated).isEqualTo(2);

        long remainingUnread = repository.countByRecipientIdAndReadFalse(userA);
        assertThat(remainingUnread).isEqualTo(0L);
    }

    @Test
    @DisplayName("Idempotency lookup by sourceService and sourceEventId")
    void testIdempotencyLookup() {
        Notification notification = Notification.builder()
                .recipientId(userA)
                .type(NotificationType.DOCUMENT_PROCESSING)
                .title("Doc Processed")
                .message("Event message")
                .sourceService("PROCESSING_SERVICE")
                .sourceEventId("event-unique-999")
                .read(false)
                .build();

        repository.save(notification);

        Optional<Notification> found =
                repository.findBySourceServiceAndSourceEventId("PROCESSING_SERVICE", "event-unique-999");
        assertThat(found).isPresent();

        Optional<Notification> missing =
                repository.findBySourceServiceAndSourceEventId("PROCESSING_SERVICE", "non-existent-event");
        assertThat(missing).isEmpty();
    }
}
