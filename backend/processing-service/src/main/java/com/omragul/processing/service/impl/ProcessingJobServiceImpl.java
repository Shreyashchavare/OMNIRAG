package com.omragul.processing.service.impl;

import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.DocumentProcessingStatusResponse;
import com.omragul.processing.dto.response.JobStatusResponse;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.AccessDeniedException;
import com.omragul.processing.exception.DocumentNotFoundException;
import com.omragul.processing.exception.DuplicateJobException;
import com.omragul.processing.exception.IllegalStateTransitionException;
import com.omragul.processing.exception.InvalidDocumentStateException;
import com.omragul.processing.exception.JobNotFoundException;
import com.omragul.processing.exception.KnowledgeBaseMismatchException;
import com.omragul.processing.exception.MaxRetriesExceededException;
import com.omragul.processing.exception.UnauthorizedException;
import com.omragul.processing.repository.ProcessingJobRepository;
import com.omragul.processing.security.UserSecurityContext;
import com.omragul.processing.security.UserSecurityContextHolder;
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
            ProcessingStatus.EMBEDDING_INDEXING);

    private final ProcessingJobRepository repository;
    private final ProcessingOrchestrator orchestrator;
    private final KnowledgeServiceClient knowledgeServiceClient;
    private final com.omragul.processing.security.SecurityAuditLogger securityAuditLogger;
    private final int defaultMaxAttempts;

    @org.springframework.beans.factory.annotation.Autowired
    public ProcessingJobServiceImpl(
            ProcessingJobRepository repository,
            @Lazy ProcessingOrchestrator orchestrator,
            KnowledgeServiceClient knowledgeServiceClient,
            com.omragul.processing.security.SecurityAuditLogger securityAuditLogger,
            @Value("${processing.orchestration.max-attempts:3}") int defaultMaxAttempts) {
        this.repository = repository;
        this.orchestrator = orchestrator;
        this.knowledgeServiceClient = knowledgeServiceClient;
        this.securityAuditLogger = securityAuditLogger;
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    public ProcessingJobServiceImpl(
            ProcessingJobRepository repository,
            @Lazy ProcessingOrchestrator orchestrator,
            KnowledgeServiceClient knowledgeServiceClient,
            @Value("${processing.orchestration.max-attempts:3}") int defaultMaxAttempts) {
        this(repository, orchestrator, knowledgeServiceClient,
                new com.omragul.processing.security.SecurityAuditLogger(), defaultMaxAttempts);
    }

    @Override
    @Transactional
    public ProcessingJobResponse createJob(ProcessingJobCreateRequest request) {
        log.info("Request to create processing job for document: {}", request.getDocumentId());

        UserSecurityContext context = UserSecurityContextHolder.getContext();
        UUID callerUserId = (context != null) ? context.getUserId() : null;
        boolean isInternal = (context != null && context.isInternalService());
        boolean isAdmin = (context != null && context.isAdmin());

        // PROC-06 & VULN-3.3-02: Authoritative Document Validation via Knowledge
        // Service
        DocumentDto document = knowledgeServiceClient.getDocument(request.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(request.getDocumentId()));

        if (document.getKnowledgeBaseId() == null) {
            throw new InvalidDocumentStateException(request.getDocumentId(),
                    "Document is not associated with any knowledge base");
        }

        if (request.getKnowledgeBaseId() != null
                && !request.getKnowledgeBaseId().equals(document.getKnowledgeBaseId())) {
            throw new KnowledgeBaseMismatchException(request.getKnowledgeBaseId(), document.getKnowledgeBaseId());
        }

        // Verify document write access via authoritative Knowledge Service check
        if (!isInternal && !isAdmin) {
            if (callerUserId == null) {
                throw new UnauthorizedException("Authentication required: missing user identity");
            }
            boolean hasWriteAccess = knowledgeServiceClient.verifyDocumentWriteAccess(request.getDocumentId(),
                    callerUserId);
            if (!hasWriteAccess) {
                securityAuditLogger.logAccessDenied(callerUserId, "CREATE_JOB", request.getDocumentId(),
                        "User lacks WRITE or OWNER permission on document");
                // Conceal document existence to prevent probing
                throw new DocumentNotFoundException(request.getDocumentId());
            }
        }

        UUID effectiveKnowledgeBaseId = document.getKnowledgeBaseId();
        UUID effectiveOwnerId = (callerUserId != null) ? callerUserId : document.getOwnerId();

        // 1. Idempotency Check
        String trimmedKey = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey().trim()
                : null;

        if (trimmedKey != null) {
            Optional<ProcessingJob> existingByIdempotency = repository
                    .findByDocumentIdAndIdempotencyKey(request.getDocumentId(), trimmedKey);
            if (existingByIdempotency.isPresent()) {
                ProcessingJob existingJob = existingByIdempotency.get();
                if (!isInternal && !isAdmin && existingJob.getOwnerId() != null && callerUserId != null) {
                    if (!existingJob.getOwnerId().equals(callerUserId)) {
                        throw new JobNotFoundException("Job not found");
                    }
                }
                log.info("Returning existing job {} for document {} and idempotency key {}",
                        existingJob.getId(), request.getDocumentId(), trimmedKey);
                return ProcessingJobResponse.fromEntity(existingJob);
            }
        }

        // 2. Active Job Check (prevent duplicate concurrent processing of same
        // document)
        Optional<ProcessingJob> activeJob = repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(
                request.getDocumentId(),
                ACTIVE_STATUSES);
        if (activeJob.isPresent()) {
            log.warn("Document {} is already being processed by active job {}", request.getDocumentId(),
                    activeJob.get().getId());
            throw new DuplicateJobException(
                    String.format("Document %s is already being processed by active job %s", request.getDocumentId(),
                            activeJob.get().getId()),
                    activeJob.get().getId());
        }

        // 3. Persist new Job
        ProcessingJob job = ProcessingJob.builder()
                .documentId(request.getDocumentId())
                .knowledgeBaseId(effectiveKnowledgeBaseId)
                .ownerId(effectiveOwnerId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .currentStep("Job enqueued for execution")
                .attemptCount(0)
                .maxAttempts(defaultMaxAttempts)
                .idempotencyKey(trimmedKey)
                .build();

        ProcessingJob saved;
        try {
            saved = repository.saveAndFlush(job);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate job submission detected for document {} and idempotency key {}",
                    request.getDocumentId(), trimmedKey);
            throw new DuplicateJobException(
                    String.format("Duplicate processing job for document %s already exists or is being created",
                            request.getDocumentId()),
                    null);
        }
        log.info("Created processing job: {} for document: {} (owner: {})", saved.getId(), saved.getDocumentId(),
                saved.getOwnerId());

        // 4. Asynchronous Execution Dispatch after transaction commit (PROC-08)
        dispatchJobAfterCommit(saved.getId());

        return ProcessingJobResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingJobResponse getJobById(UUID jobId) {
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        verifyJobOwnership(job, jobId);
        return ProcessingJobResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(UUID jobId) {
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        verifyJobOwnership(job, jobId);
        return JobStatusResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentProcessingStatusResponse getDocumentStatus(UUID documentId) {
        UserSecurityContext context = UserSecurityContextHolder.getContext();
        Optional<ProcessingJob> jobOpt;
        if (context != null && !context.isInternalService() && !context.isAdmin() && context.getUserId() != null) {
            jobOpt = repository.findFirstByDocumentIdAndOwnerIdOrderByCreatedAtDesc(documentId, context.getUserId());
        } else {
            jobOpt = repository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId);
        }
        ProcessingJob job = jobOpt
                .orElseThrow(() -> new JobNotFoundException("No processing jobs found for document: " + documentId));
        return DocumentProcessingStatusResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProcessingJobResponse> getJobs(ProcessingStatus status, Pageable pageable) {
        UserSecurityContext context = UserSecurityContextHolder.getContext();
        Page<ProcessingJob> page;
        if (context != null && !context.isInternalService() && !context.isAdmin() && context.getUserId() != null) {
            UUID ownerId = context.getUserId();
            if (status != null) {
                page = repository.findByOwnerIdAndStatus(ownerId, status, pageable);
            } else {
                page = repository.findByOwnerId(ownerId, pageable);
            }
        } else if (context != null && context.isAdmin()) {
            securityAuditLogger.logAdminOverride(context.getUserId(), "LIST_ALL_JOBS", null,
                    "Administrative unscoped job listing");
            if (status != null) {
                page = repository.findByStatus(status, pageable);
            } else {
                page = repository.findAll(pageable);
            }
        } else if (context != null && context.isInternalService()) {
            securityAuditLogger.logAccessDenied(null, "LIST_ALL_JOBS", null,
                    "Internal service attempted unscoped job listing");
            throw new AccessDeniedException("Internal services cannot perform unscoped job listings");
        } else {
            throw new UnauthorizedException("Authentication required");
        }
        return page.map(ProcessingJobResponse::fromEntity);
    }

    @Override
    @Transactional
    public ProcessingJobResponse retryJob(UUID jobId) {
        log.info("Request to retry processing job: {}", jobId);
        UserSecurityContext context = UserSecurityContextHolder.getContext();
        if (context != null && context.isInternalService() && !context.isAdmin()) {
            securityAuditLogger.logAccessDenied(null, "RETRY_JOB", jobId,
                    "Internal service cannot arbitrarily retry user jobs");
            throw new AccessDeniedException("Internal services cannot arbitrarily retry user jobs");
        }
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        verifyJobOwnership(job, jobId);

        if (job.getStatus() != ProcessingStatus.FAILED) {
            throw new IllegalStateTransitionException(
                    String.format(
                            "Job %s cannot be retried because it is in state %s. Only FAILED jobs can be retried.",
                            jobId, job.getStatus()));
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
        log.info("Job {} re-enqueued for retry (attempt {}/{})", jobId, saved.getAttemptCount() + 1,
                saved.getMaxAttempts());

        // Asynchronous Execution Dispatch after transaction commit (PROC-08)
        dispatchJobAfterCommit(saved.getId());

        return ProcessingJobResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProcessingJobResponse cancelJob(UUID jobId) {
        log.info("Request to cancel processing job: {}", jobId);
        UserSecurityContext context = UserSecurityContextHolder.getContext();
        if (context != null && context.isInternalService() && !context.isAdmin()) {
            securityAuditLogger.logAccessDenied(null, "CANCEL_JOB", jobId,
                    "Internal service cannot arbitrarily cancel user jobs");
            throw new AccessDeniedException("Internal services cannot arbitrarily cancel user jobs");
        }
        ProcessingJob job = repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        verifyJobOwnership(job, jobId);

        if (job.getStatus().isTerminal()) {
            throw new IllegalStateTransitionException(
                    String.format("Cannot cancel job %s because it is already in terminal state %s", jobId,
                            job.getStatus()));
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

    private void verifyJobOwnership(ProcessingJob job, UUID jobId) {
        UserSecurityContext context = UserSecurityContextHolder.getContext();
        if (context == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (context.isAdmin()) {
            securityAuditLogger.logAdminOverride(context.getUserId(), "JOB_ACCESS", jobId,
                    "Admin accessed job " + jobId);
            return;
        }
        if (context.isInternalService()) {
            return;
        }
        UUID callerUserId = context.getUserId();
        // If legacy ownerless job (ownerId == null) or owner does not match
        // authenticated user:
        if (job.getOwnerId() == null || callerUserId == null || !job.getOwnerId().equals(callerUserId)) {
            securityAuditLogger.logAccessDenied(callerUserId, "JOB_ACCESS", jobId, "User does not own job " + jobId);
            // Conceal existence to prevent IDOR / probing
            throw new JobNotFoundException(jobId);
        }
    }

    /**
     * PROC-08: Asynchronous task dispatch strictly after transaction commit.
     * When an active Spring transaction is present, task submission is registered
     * with
     * TransactionSynchronizationManager.afterCommit() so asynchronous worker
     * threads cannot
     * observe uncommitted pre-retry state (e.g., FAILED) or race with commit.
     * When no transaction is active (e.g. standalone test invocations), dispatches
     * directly.
     * Executor rejections are trapped and logged without failing committed state.
     */
    private void dispatchJobAfterCommit(UUID jobId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                orchestrator.submitJob(jobId);
                            } catch (Exception e) {
                                log.error("Failed to submit job {} to executor after transaction commit: {}", jobId,
                                        e.getMessage(), e);
                            }
                        }
                    });
        } else {
            try {
                orchestrator.submitJob(jobId);
            } catch (Exception e) {
                log.error("Failed to submit job {} to executor: {}", jobId, e.getMessage(), e);
            }
        }
    }
}
