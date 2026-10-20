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
@Schema(description = "Lightweight status query response for a processing job")
public class JobStatusResponse {

    @Schema(description = "Job unique identifier")
    private UUID id;

    @Schema(description = "Document UUID")
    private UUID documentId;

    @Schema(description = "Current lifecycle status")
    private ProcessingStatus status;

    @Schema(description = "Current processing stage")
    private ProcessingStage stage;

    @Schema(description = "Detailed current execution step")
    private String currentStep;

    @Schema(description = "Attempt count")
    private Integer attemptCount;

    @Schema(description = "Safe failure error code if failed")
    private String errorCode;

    @Schema(description = "Safe failure error message if failed")
    private String errorMessage;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    public static JobStatusResponse fromEntity(ProcessingJob job) {
        if (job == null) {
            return null;
        }
        return JobStatusResponse.builder()
                .id(job.getId())
                .documentId(job.getDocumentId())
                .status(job.getStatus())
                .stage(job.getStage())
                .currentStep(job.getCurrentStep())
                .attemptCount(job.getAttemptCount())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
