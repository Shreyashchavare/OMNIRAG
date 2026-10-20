package com.omragul.processing.dto.response;

import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed processing job response")
public class ProcessingJobResponse {

    @Schema(description = "Job unique identifier")
    private UUID id;

    @Schema(description = "Document UUID")
    private UUID documentId;

    @Schema(description = "Knowledge Base UUID")
    private UUID knowledgeBaseId;

    @Schema(description = "Current lifecycle status")
    private ProcessingStatus status;

    @Schema(description = "Current processing stage")
    private ProcessingStage stage;

    @Schema(description = "Detailed current execution step")
    private String currentStep;

    @Schema(description = "Number of execution attempts")
    private Integer attemptCount;

    @Schema(description = "Maximum permitted attempts")
    private Integer maxAttempts;

    @Schema(description = "Safe failure error code if failed")
    private String errorCode;

    @Schema(description = "Safe failure error message if failed")
    private String errorMessage;

    @Schema(description = "Idempotency key if supplied")
    private String idempotencyKey;

    @Schema(description = "Timestamp when job was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when job was last updated")
    private Instant updatedAt;

    @Schema(description = "Timestamp when execution started")
    private Instant startedAt;

    @Schema(description = "Timestamp when execution completed or failed")
    private Instant completedAt;

    public static ProcessingJobResponse fromEntity(ProcessingJob job) {
        if (job == null) {
            return null;
        }
        return ProcessingJobResponse.builder()
                .id(job.getId())
                .documentId(job.getDocumentId())
                .knowledgeBaseId(job.getKnowledgeBaseId())
                .status(job.getStatus())
                .stage(job.getStage())
                .currentStep(job.getCurrentStep())
                .attemptCount(job.getAttemptCount())
                .maxAttempts(job.getMaxAttempts())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .idempotencyKey(job.getIdempotencyKey())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}
