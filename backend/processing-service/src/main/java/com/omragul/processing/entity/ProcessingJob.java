package com.omragul.processing.entity;

import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "processing_jobs",
        indexes = {
                @Index(name = "idx_processing_jobs_document_id", columnList = "document_id"),
                @Index(name = "idx_processing_jobs_owner_id", columnList = "owner_id"),
                @Index(name = "idx_processing_jobs_status", columnList = "status"),
                @Index(name = "idx_processing_jobs_idempotency_key", columnList = "idempotency_key"),
                @Index(name = "idx_processing_jobs_created_at", columnList = "created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_processing_jobs_doc_idempotency", columnNames = {"document_id", "idempotency_key"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "knowledge_base_id")
    private UUID knowledgeBaseId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private ProcessingStatus status = ProcessingStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 30, nullable = false)
    @Builder.Default
    private ProcessingStage stage = ProcessingStage.INITIALIZATION;

    @Column(name = "current_step", length = 500)
    private String currentStep;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.attemptCount == null) {
            this.attemptCount = 0;
        }
        if (this.maxAttempts == null) {
            this.maxAttempts = 3;
        }
        if (this.status == null) {
            this.status = ProcessingStatus.QUEUED;
        }
        if (this.stage == null) {
            this.stage = ProcessingStage.INITIALIZATION;
        }
        truncateCurrentStepIfNeeded();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
        truncateCurrentStepIfNeeded();
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
        truncateCurrentStepIfNeeded();
    }

    private void truncateCurrentStepIfNeeded() {
        if (this.currentStep != null && this.currentStep.length() > 500) {
            this.currentStep = this.currentStep.substring(0, 497) + "...";
        }
    }

    public boolean canRetry() {
        return this.status == ProcessingStatus.FAILED && this.attemptCount < this.maxAttempts;
    }
}
