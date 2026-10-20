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
@Schema(description = "Latest processing status for a specific document")
public class DocumentProcessingStatusResponse {

    @Schema(description = "Document unique identifier")
    private UUID documentId;

    @Schema(description = "Latest job unique identifier")
    private UUID latestJobId;

    @Schema(description = "Current lifecycle status")
    private ProcessingStatus status;

    @Schema(description = "Current processing stage")
    private ProcessingStage stage;

    @Schema(description = "Detailed current execution step")
    private String currentStep;

    @Schema(description = "Timestamp of last update")
    private Instant updatedAt;

    public static DocumentProcessingStatusResponse fromEntity(ProcessingJob job) {
        if (job == null) {
            return null;
        }
        return DocumentProcessingStatusResponse.builder()
                .documentId(job.getDocumentId())
                .latestJobId(job.getId())
                .status(job.getStatus())
                .stage(job.getStage())
                .currentStep(job.getCurrentStep())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
