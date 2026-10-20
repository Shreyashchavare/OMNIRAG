package com.omragul.processing.controller;

import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.DocumentProcessingStatusResponse;
import com.omragul.processing.dto.response.ErrorResponseDto;
import com.omragul.processing.dto.response.JobStatusResponse;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.service.ProcessingJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/processing")
@RequiredArgsConstructor
@Tag(name = "Processing Service", description = "Document processing lifecycle, orchestration, and status query APIs")
public class ProcessingJobController {

    private final ProcessingJobService processingJobService;

    @PostMapping("/jobs")
    @Operation(summary = "Create processing job", description = "Initiates asynchronous document processing pipeline")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Job created and enqueued successfully",
                    content = @Content(schema = @Schema(implementation = ProcessingJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Document is already being processed by an active job",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ProcessingJobResponse> createJob(
            @Valid @RequestBody ProcessingJobCreateRequest request
    ) {
        ProcessingJobResponse response = processingJobService.createJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Get job by ID", description = "Retrieves full details of a processing job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job details found",
                    content = @Content(schema = @Schema(implementation = ProcessingJobResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ProcessingJobResponse> getJobById(
            @Parameter(description = "Processing Job UUID") @PathVariable UUID id
    ) {
        ProcessingJobResponse response = processingJobService.getJobById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs/{id}/status")
    @Operation(summary = "Get job status", description = "Retrieves lightweight status and current progress of a processing job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job status found",
                    content = @Content(schema = @Schema(implementation = JobStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @Parameter(description = "Processing Job UUID") @PathVariable UUID id
    ) {
        JobStatusResponse response = processingJobService.getJobStatus(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents/{documentId}/status")
    @Operation(summary = "Get document processing status", description = "Retrieves latest processing job status for a document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document processing status found",
                    content = @Content(schema = @Schema(implementation = DocumentProcessingStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "No processing jobs found for document",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DocumentProcessingStatusResponse> getDocumentStatus(
            @Parameter(description = "Document UUID") @PathVariable UUID documentId
    ) {
        DocumentProcessingStatusResponse response = processingJobService.getDocumentStatus(documentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs")
    @Operation(summary = "List processing jobs", description = "Retrieves paginated processing jobs with optional status filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of processing jobs")
    })
    public ResponseEntity<Page<ProcessingJobResponse>> getJobs(
            @Parameter(description = "Filter by job status (optional)") @RequestParam(required = false) ProcessingStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProcessingJobResponse> response = processingJobService.getJobs(status, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/jobs/{id}/retry")
    @Operation(summary = "Retry failed job", description = "Re-enqueues a failed processing job if under max retry attempts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job re-enqueued for retry",
                    content = @Content(schema = @Schema(implementation = ProcessingJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Job is not in FAILED state or max retries exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ProcessingJobResponse> retryJob(
            @Parameter(description = "Processing Job UUID") @PathVariable UUID id
    ) {
        ProcessingJobResponse response = processingJobService.retryJob(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/jobs/{id}/cancel")
    @Operation(summary = "Cancel processing job", description = "Cancels an active processing job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job cancelled successfully",
                    content = @Content(schema = @Schema(implementation = ProcessingJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Job is already in a terminal state",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ProcessingJobResponse> cancelJob(
            @Parameter(description = "Processing Job UUID") @PathVariable UUID id
    ) {
        ProcessingJobResponse response = processingJobService.cancelJob(id);
        return ResponseEntity.ok(response);
    }
}
