package com.omragul.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(
        name = "knowledge_bases",
        indexes = {
                @Index(name = "idx_kb_owner", columnList = "owner_id"),
                @Index(name = "idx_kb_status", columnList = "status"),
                @Index(name = "idx_kb_visibility", columnList = "visibility"),
                @Index(name = "idx_kb_name", columnList = "name")
        }
)
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "slug", length = 200, nullable = false, unique = true)
    private String slug;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    private Visibility visibility = Visibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "total_documents", nullable = false)
    private Integer totalDocuments = 0;

    @Column(name = "total_size_bytes", nullable = false)
    private Long totalSizeBytes = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    public enum Visibility {
        PRIVATE,
        PUBLIC,
        SHARED
    }

    public enum Status {
        ACTIVE,
        ARCHIVED,
        DELETED
    }

    public KnowledgeBase() {
    }

}