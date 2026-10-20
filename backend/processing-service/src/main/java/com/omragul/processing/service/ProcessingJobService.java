package com.omragul.processing.service;

import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.DocumentProcessingStatusResponse;
import com.omragul.processing.dto.response.JobStatusResponse;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProcessingJobService {

    ProcessingJobResponse createJob(ProcessingJobCreateRequest request);

    ProcessingJobResponse getJobById(UUID jobId);

    JobStatusResponse getJobStatus(UUID jobId);

    DocumentProcessingStatusResponse getDocumentStatus(UUID documentId);

    Page<ProcessingJobResponse> getJobs(ProcessingStatus status, Pageable pageable);

    ProcessingJobResponse retryJob(UUID jobId);

    ProcessingJobResponse cancelJob(UUID jobId);

    ProcessingJob getJobEntity(UUID jobId);

    void updateJobStatus(UUID jobId, ProcessingStatus newStatus, ProcessingStage newStage, String currentStep);

    void markJobFailed(UUID jobId, String errorCode, String errorMessage);

    void markJobCompleted(UUID jobId);
}
