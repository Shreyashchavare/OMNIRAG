package com.omragul.processing.service.impl;

import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.DocumentProcessingStatusResponse;
import com.omragul.processing.dto.response.JobStatusResponse;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.DuplicateJobException;
import com.omragul.processing.exception.IllegalStateTransitionException;
import com.omragul.processing.exception.JobNotFoundException;
import com.omragul.processing.exception.MaxRetriesExceededException;
import com.omragul.processing.repository.ProcessingJobRepository;
import com.omragul.processing.service.ProcessingJobService;
import com.omragul.processing.service.ProcessingOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ProcessingJobServiceImpl implements ProcessingJobService {

    private static final List<ProcessingStatus> ACTIVE_STATUSES = List.of(
            ProcessingStatus.QUEUED,
            ProcessingStatus.VALIDATING,
            ProcessingStatus.EXTRACTING,
            ProcessingStatus.CHUNKING,
            ProcessingStatus.EMBEDDING_INDEXING
    );

    private final ProcessingJobRepository repository;
    private final ProcessingOrchestrator orchestrator;
    private final int defaultMaxAttempts;

    public ProcessingJobServiceImpl(
            ProcessingJobRepository repository,
            @Lazy ProcessingOrchestrator orchestrator,
            @Value("${processing.orchestration.max-attempts:3}") int defaultMaxAttempts
    ) {
        this.repository = repository;
        this.orchestrator = orchestrator;
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    @Override
    @Transactional
    public ProcessingJobResponse createJob(ProcessingJobCreateRequest request) {
        log.info("Request to create processing job for document: {}", request.getDocumentId());

        // 1. Idempotency Check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<ProcessingJob> existingByIdempotency = repository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existingByIdempotency.isPresent()) {
                log.info("Returning existing job {} for idempotency key {}", existingByIdempotency.get().getId(), request.getIdempotencyKey());
                return ProcessingJobResponse.fromEntity(existingByIdempotency.get());
            }
        }

        // 2. Active Job Check (prevent duplicate concurrent processing of same document)
        Optional<ProcessingJob> activeJob = repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(
                request.getDocumentId(),
                ACTIVE_STATUSES
        );
        if (activeJob.isPresent()) {
            log.warn("Document {} is already being processed by active job {}", request.getDocumentId(), activeJob.get().getId());
            throw new DuplicateJobException(
                    String.format("Document %s is already being processed by active job %s", request.getDocumentId(), activeJob.get().getId()),
                    activeJob.get().getId()
            );
        }

        // 3. Persist new Job
        ProcessingJob job = ProcessingJob.builder()
                .documentId(request.getDocumentId())
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .currentStep("Job enqueued for execution")
                .attemptCount(0)
                .maxAttempts(defaultMaxAttempts)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        ProcessingJob saved = repository.save(job);
        log.info("Created processing job: {} for document: {}", saved.getId(), saved.getDocumentId());

        // 4. Asynchronous Execution Dispatch
        orchestrator.submitJob(saved.getId());

        return ProcessingJobResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingJobResponse getJobById(UUID jobId) {
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return ProcessingJobResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(UUID jobId) {
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return JobStatusResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentProcessingStatusResponse getDocumentStatus(UUID documentId) {
        ProcessingJob job = repository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
                .orElseThrow(() -> new JobNotFoundException("No processing jobs found for document: " + documentId));
        return DocumentProcessingStatusResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProcessingJobResponse> getJobs(ProcessingStatus status, Pageable pageable) {
        Page<ProcessingJob> page;
        if (status != null) {
            page = repository.findByStatus(status, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return page.map(ProcessingJobResponse::fromEntity);
    }

    @Override
    @Transactional
    public ProcessingJobResponse retryJob(UUID jobId) {
        log.info("Request to retry processing job: {}", jobId);
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.getStatus() != ProcessingStatus.FAILED) {
            throw new IllegalStateTransitionException(
                    String.format("Job %s cannot be retried because it is in state %s. Only FAILED jobs can be retried.", jobId, job.getStatus())
            );
        }

        if (job.getAttemptCount() >= job.getMaxAttempts()) {
            throw new MaxRetriesExceededException(jobId, job.getAttemptCount(), job.getMaxAttempts());
        }

        if (!job.getStatus().canTransitionTo(ProcessingStatus.QUEUED)) {
            throw new IllegalStateTransitionException(job.getStatus(), ProcessingStatus.QUEUED);
        }

        job.setStatus(ProcessingStatus.QUEUED);
        job.setStage(ProcessingStage.INITIALIZATION);
        job.setCurrentStep("Job re-enqueued for retry");
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setCompletedAt(null);

        ProcessingJob saved = repository.save(job);
        log.info("Job {} re-enqueued for retry (attempt {}/{})", jobId, saved.getAttemptCount() + 1, saved.getMaxAttempts());

        orchestrator.submitJob(saved.getId());

        return ProcessingJobResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProcessingJobResponse cancelJob(UUID jobId) {
        log.info("Request to cancel processing job: {}", jobId);
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.getStatus().isTerminal()) {
            throw new IllegalStateTransitionException(
                    String.format("Cannot cancel job %s because it is already in terminal state %s", jobId, job.getStatus())
            );
        }

        if (!job.getStatus().canTransitionTo(ProcessingStatus.CANCELLED)) {
            throw new IllegalStateTransitionException(job.getStatus(), ProcessingStatus.CANCELLED);
        }

        job.setStatus(ProcessingStatus.CANCELLED);
        job.setStage(ProcessingStage.CANCELLED);
        job.setCurrentStep("Job cancelled by client request");
        job.setCompletedAt(Instant.now());

        ProcessingJob saved = repository.save(job);
        log.info("Job {} successfully cancelled", jobId);

        return ProcessingJobResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingJob getJobEntity(UUID jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    @Override
    @Transactional
    public void updateJobStatus(UUID jobId, ProcessingStatus newStatus, ProcessingStage newStage, String currentStep) {
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (!job.getStatus().canTransitionTo(newStatus)) {
            throw new IllegalStateTransitionException(job.getStatus(), newStatus);
        }

        job.setStatus(newStatus);
        job.setStage(newStage);
        job.setCurrentStep(currentStep);

        if (newStatus == ProcessingStatus.VALIDATING && job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        }

        repository.save(job);
        log.info("Job {} transitioned to status={}, stage={}, step={}", jobId, newStatus, newStage, currentStep);
    }

    @Override
    @Transactional
    public void markJobFailed(UUID jobId, String errorCode, String errorMessage) {
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        job.setStatus(ProcessingStatus.FAILED);
        job.setStage(ProcessingStage.FAILED);
        job.setErrorCode(errorCode);
        job.setErrorMessage(errorMessage);
        job.setCurrentStep("Execution failed: " + errorCode);
        job.setCompletedAt(Instant.now());

        repository.save(job);
        log.error("Job {} marked FAILED with code={}: {}", jobId, errorCode, errorMessage);
    }

    @Override
    @Transactional
    public void markJobCompleted(UUID jobId) {
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        job.setStatus(ProcessingStatus.COMPLETED);
        job.setStage(ProcessingStage.COMPLETED);
        job.setCurrentStep("Document processing and indexing completed successfully");
        job.setCompletedAt(Instant.now());

        repository.save(job);
        log.info("Job {} marked COMPLETED successfully", jobId);
    }
}
