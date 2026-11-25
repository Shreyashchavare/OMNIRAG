package com.omragul.processing.service.impl;

import com.omragul.processing.client.EmbeddingServiceClient;
import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.MultimodalServiceClient;
import com.omragul.processing.client.RagServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.DocumentNotFoundException;
import com.omragul.processing.exception.DownstreamServiceException;
import com.omragul.processing.exception.PermanentProcessingException;
import com.omragul.processing.exception.TransientProcessingException;
import com.omragul.processing.repository.ProcessingJobRepository;
import com.omragul.processing.service.ProcessingOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Orchestrator implementing pipeline execution, delayed retry scheduling with exponential backoff (PROC-03 Scope A),
 * runtime recovery watchdog for stale jobs (PROC-03 Scope B), and staggered startup recovery (PROC-03 Scope C).
 */
@Slf4j
@Service
public class DefaultProcessingOrchestrator implements ProcessingOrchestrator {

    private static final List<ProcessingStatus> IN_FLIGHT_STATUSES = List.of(
            ProcessingStatus.VALIDATING,
            ProcessingStatus.EXTRACTING,
            ProcessingStatus.CHUNKING,
            ProcessingStatus.EMBEDDING_INDEXING
    );

    private final ProcessingJobRepository repository;
    private final KnowledgeServiceClient knowledgeServiceClient;
    private final MultimodalServiceClient multimodalServiceClient;
    private final EmbeddingServiceClient embeddingServiceClient;
    private final RagServiceClient ragServiceClient;
    private final Executor taskExecutor;
    private final TaskScheduler taskScheduler;

    /**
     * In-memory deduplication set tracking job IDs currently scheduled for delayed retry.
     * Prevents duplicate scheduling and informs watchdog to skip jobs in backoff delay.
     */
    private final Set<UUID> scheduledJobIds = ConcurrentHashMap.newKeySet();

    @Value("${processing.orchestration.retry-initial-delay-ms:1000}")
    private long retryInitialDelayMs = 1000L;

    @Value("${processing.orchestration.retry-max-delay-ms:30000}")
    private long retryMaxDelayMs = 30000L;

    @Value("${processing.orchestration.retry-backoff-multiplier:2.0}")
    private double retryBackoffMultiplier = 2.0;

    @Value("${processing.recovery.stale-processing-threshold-ms:300000}")
    private long staleInFlightThresholdMs = 300000L; // 5 minutes

    @Value("${processing.recovery.queued-stale-threshold-ms:120000}")
    private long staleQueuedThresholdMs = 120000L; // 2 minutes

    @Value("${processing.recovery.batch-size:20}")
    private int recoveryBatchSize = 20;

    @Value("${processing.recovery.startup-stagger-ms:200}")
    private long startupStaggerMs = 200L;

    @Autowired
    public DefaultProcessingOrchestrator(
            ProcessingJobRepository repository,
            KnowledgeServiceClient knowledgeServiceClient,
            MultimodalServiceClient multimodalServiceClient,
            EmbeddingServiceClient embeddingServiceClient,
            RagServiceClient ragServiceClient,
            @Qualifier("processingTaskExecutor") Executor taskExecutor,
            @Qualifier("processingTaskScheduler") TaskScheduler taskScheduler) {
        this.repository = repository;
        this.knowledgeServiceClient = knowledgeServiceClient;
        this.multimodalServiceClient = multimodalServiceClient;
        this.embeddingServiceClient = embeddingServiceClient;
        this.ragServiceClient = ragServiceClient;
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Backward-compatible constructor for existing tests and standalone instantiation.
     */
    public DefaultProcessingOrchestrator(
            ProcessingJobRepository repository,
            KnowledgeServiceClient knowledgeServiceClient,
            MultimodalServiceClient multimodalServiceClient,
            EmbeddingServiceClient embeddingServiceClient,
            RagServiceClient ragServiceClient,
            Executor taskExecutor) {
        this(repository, knowledgeServiceClient, multimodalServiceClient,
                embeddingServiceClient, ragServiceClient, taskExecutor,
                createDefaultTaskScheduler());
    }

    private static TaskScheduler createDefaultTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("default-orchestrator-scheduler-");
        scheduler.initialize();
        return scheduler;
    }

    // Setters for testability and runtime reconfiguration
    public void setRetryInitialDelayMs(long retryInitialDelayMs) { this.retryInitialDelayMs = retryInitialDelayMs; }
    public void setRetryMaxDelayMs(long retryMaxDelayMs) { this.retryMaxDelayMs = retryMaxDelayMs; }
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) { this.retryBackoffMultiplier = retryBackoffMultiplier; }
    public void setStaleInFlightThresholdMs(long staleInFlightThresholdMs) { this.staleInFlightThresholdMs = staleInFlightThresholdMs; }
    public void setStaleQueuedThresholdMs(long staleQueuedThresholdMs) { this.staleQueuedThresholdMs = staleQueuedThresholdMs; }
    public void setRecoveryBatchSize(int recoveryBatchSize) { this.recoveryBatchSize = recoveryBatchSize; }
    public void setStartupStaggerMs(long startupStaggerMs) { this.startupStaggerMs = startupStaggerMs; }

    public boolean isJobScheduledForRetry(UUID jobId) {
        return scheduledJobIds.contains(jobId);
    }

    @Override
    public void submitJob(UUID jobId) {
        log.info("Submitting job {} to async processing worker pool", jobId);
        try {
            taskExecutor.execute(() -> processJobPipeline(jobId));
        } catch (RejectedExecutionException e) {
            log.error("Task executor rejected job {}: {}. Job remains in QUEUED status in DB for recovery.",
                    jobId, e.getMessage());
        }
    }

    /**
     * PROC-03 Scope A: Calculates bounded exponential backoff delay based on attempt count.
     * Backoff = initialDelay * (multiplier ^ (attemptCount - 1)), bounded by maxDelay.
     */
    public long calculateRetryDelayMs(int attemptCount) {
        if (attemptCount <= 1) {
            return Math.min(retryInitialDelayMs, retryMaxDelayMs);
        }
        double backoff = retryInitialDelayMs * Math.pow(retryBackoffMultiplier, attemptCount - 1);
        if (Double.isInfinite(backoff) || backoff > retryMaxDelayMs) {
            return retryMaxDelayMs;
        }
        return Math.max(0L, (long) backoff);
    }

    /**
     * PROC-03 Scope A: Schedules a delayed retry using TaskScheduler with deduplication
     * and graceful rejection handling.
     */
    public void scheduleDelayedRetry(UUID jobId, long delayMs) {
        if (!scheduledJobIds.add(jobId)) {
            log.warn("[Job {}] Retry is already scheduled, skipping duplicate schedule registration", jobId);
            return;
        }
        try {
            log.info("[Job {}] Scheduling retry task in {} ms", jobId, delayMs);
            taskScheduler.schedule(() -> {
                try {
                    scheduledJobIds.remove(jobId);
                    submitJob(jobId);
                } catch (Exception e) {
                    log.error("[Job {}] Error executing scheduled retry submission: {}", jobId, e.getMessage(), e);
                }
            }, Instant.now().plusMillis(Math.max(0, delayMs)));
        } catch (RejectedExecutionException e) {
            scheduledJobIds.remove(jobId);
            log.error("[Job {}] Task scheduler rejected retry task: {}. Job remains in QUEUED status in DB for watchdog recovery.",
                    jobId, e.getMessage());
        }
    }

    /**
     * PROC-03 Scope C: Recover interrupted processing jobs on startup with staggered submissions.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        log.info("Checking for interrupted processing jobs needing recovery after startup...");
        List<ProcessingJob> interruptedJobs = repository.findByStatusIn(List.of(
                ProcessingStatus.QUEUED,
                ProcessingStatus.VALIDATING,
                ProcessingStatus.EXTRACTING,
                ProcessingStatus.CHUNKING,
                ProcessingStatus.EMBEDDING_INDEXING));

        if (interruptedJobs.isEmpty()) {
            log.info("No interrupted processing jobs found. System is clean.");
            return;
        }

        log.info("Found {} interrupted job(s) for startup recovery with stagger {} ms",
                interruptedJobs.size(), startupStaggerMs);
        for (int i = 0; i < interruptedJobs.size(); i++) {
            ProcessingJob job = interruptedJobs.get(i);
            long delayMs = (long) i * startupStaggerMs;
            try {
                recoverSingleJob(job.getId(), delayMs);
            } catch (Exception e) {
                log.error("Failed to recover job {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    public void recoverSingleJob(UUID jobId) {
        recoverSingleJob(jobId, 0L);
    }

    @Transactional
    public void recoverSingleJob(UUID jobId, long delayMs) {
        Optional<ProcessingJob> optJob = repository.findById(jobId);
        if (optJob.isEmpty())
            return;

        ProcessingJob job = optJob.get();
        if (job.getStatus().isTerminal())
            return;

        if (job.getAttemptCount() < job.getMaxAttempts()) {
            log.info("Recovering in-flight job {} (attempt {}/{}), re-queueing with stagger delay {} ms",
                    jobId, job.getAttemptCount(), job.getMaxAttempts(), delayMs);
            job.setStatus(ProcessingStatus.QUEUED);
            job.setStage(ProcessingStage.INITIALIZATION);
            job.setCurrentStep(delayMs > 0
                    ? String.format("Recovered after service restart; queued for execution with %d ms stagger", delayMs)
                    : "Recovered after service restart; queued for execution");
            repository.save(job);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatchRecoveredJob(jobId, delayMs);
                    }
                });
            } else {
                dispatchRecoveredJob(jobId, delayMs);
            }
        } else {
            log.warn("Job {} reached max attempts ({}/{}) across restarts; marking FAILED",
                    jobId, job.getAttemptCount(), job.getMaxAttempts());
            job.setStatus(ProcessingStatus.FAILED);
            job.setStage(ProcessingStage.FAILED);
            job.setErrorCode("RESTART_RECOVERY_EXHAUSTED");
            job.setErrorMessage("Service restarted while job was in progress and maximum attempts were reached");
            job.setCompletedAt(Instant.now());
            repository.save(job);
            knowledgeServiceClient.updateDocumentProcessingStatus(job.getDocumentId(), "FAILED");
        }
    }

    private void dispatchRecoveredJob(UUID jobId, long delayMs) {
        if (delayMs <= 0) {
            submitJob(jobId);
        } else {
            try {
                taskScheduler.schedule(() -> submitJob(jobId), Instant.now().plusMillis(delayMs));
            } catch (RejectedExecutionException e) {
                log.warn("[Job {}] Task scheduler rejected staggered startup dispatch: {}. Falling back to immediate submission.",
                        jobId, e.getMessage());
                submitJob(jobId);
            }
        }
    }

    /**
     * PROC-03 Scope B: Runtime recovery watchdog executing on fixed delay.
     * Recovers stale in-flight jobs and stalled QUEUED jobs across the cluster.
     */
    @Scheduled(fixedDelayString = "${processing.recovery.watchdog-interval-ms:60000}",
               initialDelayString = "${processing.recovery.watchdog-initial-delay-ms:30000}")
    public void runRecoveryWatchdog() {
        log.debug("Running processing job recovery watchdog...");
        try {
            recoverStaleInFlightJobs();
        } catch (Exception e) {
            log.error("Error in watchdog recovering stale in-flight jobs: {}", e.getMessage(), e);
        }

        try {
            recoverStaleQueuedJobs();
        } catch (Exception e) {
            log.error("Error in watchdog recovering stale queued jobs: {}", e.getMessage(), e);
        }
    }

    /**
     * PROC-03 Scope B: Inspects in-flight jobs older than staleInFlightThreshold.
     * If attempts exhausted -> atomically marks FAILED and notifies Knowledge Service.
     * If attempts remaining -> atomically re-queues to QUEUED and dispatches.
     */
    public int recoverStaleInFlightJobs() {
        Instant now = Instant.now();
        Instant staleThreshold = now.minusMillis(staleInFlightThresholdMs);
        List<UUID> staleIds = repository.findStaleInFlightJobIds(
                IN_FLIGHT_STATUSES,
                staleThreshold,
                PageRequest.of(0, recoveryBatchSize)
        );

        if (staleIds.isEmpty()) {
            return 0;
        }

        log.info("Watchdog detected {} stale in-flight job(s) older than {}", staleIds.size(), staleThreshold);
        int recoveredCount = 0;
        for (UUID jobId : staleIds) {
            if (scheduledJobIds.contains(jobId)) {
                log.debug("[Job {}] Job is currently scheduled for backoff retry; skipping watchdog recovery", jobId);
                continue;
            }

            try {
                Optional<ProcessingJob> optJob = repository.findById(jobId);
                if (optJob.isEmpty()) continue;
                ProcessingJob job = optJob.get();

                if (job.getAttemptCount() >= job.getMaxAttempts()) {
                    log.warn("[Job {}] Stale in-flight job reached max attempts ({}/{}); marking FAILED",
                            jobId, job.getAttemptCount(), job.getMaxAttempts());
                    int updated = repository.failStaleExhaustedJobAtomic(
                            jobId,
                            IN_FLIGHT_STATUSES,
                            staleThreshold,
                            ProcessingStatus.FAILED,
                            ProcessingStage.FAILED,
                            "WATCHDOG_TIMEOUT_EXHAUSTED",
                            "In-flight job timed out and exhausted maximum attempts",
                            "Marked FAILED by watchdog: execution timed out and attempts exhausted",
                            now
                    );
                    if (updated > 0) {
                        try {
                            knowledgeServiceClient.updateDocumentProcessingStatus(job.getDocumentId(), "FAILED");
                        } catch (Exception e) {
                            log.error("[Job {}] Failed to notify Knowledge Service of FAILED status: {}", jobId, e.getMessage());
                        }
                        recoveredCount++;
                    }
                } else {
                    log.info("[Job {}] Recovering stale in-flight job (attempt {}/{}); re-queueing",
                            jobId, job.getAttemptCount(), job.getMaxAttempts());
                    int updated = repository.recoverStaleInFlightJobAtomic(
                            jobId,
                            IN_FLIGHT_STATUSES,
                            staleThreshold,
                            ProcessingStatus.QUEUED,
                            ProcessingStage.INITIALIZATION,
                            "Recovered by watchdog after stale timeout; queued for execution",
                            now
                    );
                    if (updated > 0) {
                        submitJob(jobId);
                        recoveredCount++;
                    }
                }
            } catch (Exception e) {
                log.error("[Job {}] Error recovering stale in-flight job: {}", jobId, e.getMessage(), e);
            }
        }
        return recoveredCount;
    }

    /**
     * PROC-03 Scope B: Inspects stalled QUEUED jobs older than staleQueuedThreshold.
     * Concurrency-safely updates updatedAt timestamp and re-submits to worker pool.
     */
    public int recoverStaleQueuedJobs() {
        Instant now = Instant.now();
        Instant staleThreshold = now.minusMillis(staleQueuedThresholdMs);
        List<UUID> staleQueuedIds = repository.findStaleQueuedJobIds(
                ProcessingStatus.QUEUED,
                staleThreshold,
                PageRequest.of(0, recoveryBatchSize)
        );

        if (staleQueuedIds.isEmpty()) {
            return 0;
        }

        log.info("Watchdog detected {} stalled QUEUED job(s) older than {}", staleQueuedIds.size(), staleThreshold);
        int reDispatchedCount = 0;
        for (UUID jobId : staleQueuedIds) {
            if (scheduledJobIds.contains(jobId)) {
                log.debug("[Job {}] Stalled queued job is currently scheduled for backoff retry; skipping watchdog", jobId);
                continue;
            }

            try {
                int updated = repository.touchStaleQueuedJobAtomic(
                        jobId,
                        ProcessingStatus.QUEUED,
                        staleThreshold,
                        "Re-dispatched by watchdog after stalled QUEUED timeout",
                        now
                );
                if (updated > 0) {
                    log.info("[Job {}] Successfully touched and re-dispatching stalled QUEUED job", jobId);
                    submitJob(jobId);
                    reDispatchedCount++;
                }
            } catch (Exception e) {
                log.error("[Job {}] Error re-dispatching stalled QUEUED job: {}", jobId, e.getMessage(), e);
            }
        }
        return reDispatchedCount;
    }

    /**
     * Executes the orchestration pipeline decoupled from database transactions.
     * Short transactions are used only to persist state transitions.
     */
    public void processJobPipeline(UUID jobId) {
        // 1. Atomic claim in short transaction
        Optional<ProcessingJob> claimed = claimJobForExecution(jobId);
        if (claimed.isEmpty()) {
            log.debug("Job {} was not claimable (already running, cancelled, or completed)", jobId);
            return;
        }

        ProcessingJob job = claimed.get();
        UUID documentId = job.getDocumentId();
        UUID kbId = job.getKnowledgeBaseId();

        try {
            // STEP 1: VALIDATION (Knowledge Service)
            log.info("[Job {}] Step 1: Validating document {}", jobId, documentId);
            Optional<DocumentDto> optDoc = knowledgeServiceClient.getDocument(documentId);
            if (optDoc.isEmpty()) {
                throw new PermanentProcessingException("DOCUMENT_NOT_FOUND",
                        "Document not found in Knowledge Service: " + documentId);
            }

            DocumentDto doc = optDoc.get();
            if ("DELETED".equalsIgnoreCase(doc.getStatus())) {
                throw new PermanentProcessingException("DOCUMENT_DELETED", "Document is deleted in Knowledge Service");
            }

            // Propagate KnowledgeBase ID from document if not specified on job
            if (kbId == null && doc.getKnowledgeBaseId() != null) {
                kbId = doc.getKnowledgeBaseId();
                syncKnowledgeBaseId(jobId, kbId);
            }

            if (isCancelled(jobId)) {
                log.info("[Job {}] Job cancelled after validation; halting pipeline", jobId);
                return;
            }

            // Sync status to PROCESSING in Knowledge Service
            knowledgeServiceClient.updateDocumentProcessingStatus(documentId, "PROCESSING");

            // STEP 2: EXTRACTION (Multimodal Service)
            if (!transitionStep(jobId, ProcessingStatus.EXTRACTING, ProcessingStage.EXTRACTION,
                    "Extracting text/multimodal content")) {
                log.info("[Job {}] Job cancelled before extraction; halting pipeline", jobId);
                return;
            }

            log.info("[Job {}] Step 2: Extracting text from document {}", jobId, documentId);
            String extractedText = multimodalServiceClient.extractText(doc);

            if (extractedText == null || extractedText.isBlank()) {
                throw new PermanentProcessingException("EMPTY_TEXT",
                        "No text content could be extracted from document");
            }

            if (isCancelled(jobId)) {
                log.info("[Job {}] Job cancelled after extraction; halting pipeline", jobId);
                return;
            }

            // STEP 3: CHUNKING (Multimodal Chunk Service)
            if (!transitionStep(jobId, ProcessingStatus.CHUNKING, ProcessingStage.CHUNKING,
                    "Splitting content into text chunks")) {
                log.info("[Job {}] Job cancelled before chunking; halting pipeline", jobId);
                return;
            }

            log.info("[Job {}] Step 3: Chunking content for document {}", jobId, documentId);
            List<String> chunks = multimodalServiceClient.createChunks(extractedText);

            if (chunks.isEmpty()) {
                throw new PermanentProcessingException("EMPTY_CHUNKS",
                        "Chunking produced 0 chunks from extracted text");
            }

            if (isCancelled(jobId)) {
                log.info("[Job {}] Job cancelled after chunking; halting pipeline", jobId);
                return;
            }

            // STEP 4: EMBEDDING & INDEXING (RAG Service handles vector embedding and indexing internally)
            if (!transitionStep(jobId, ProcessingStatus.EMBEDDING_INDEXING, ProcessingStage.INDEXING,
                    String.format("Generating embeddings and indexing %d chunks", chunks.size()))) {
                log.info("[Job {}] Job cancelled before indexing; halting pipeline", jobId);
                return;
            }

            log.info("[Job {}] Step 4: Indexing {} chunks via RAG Service for document {}", jobId, chunks.size(),
                    documentId);
            int indexedCount = ragServiceClient.indexChunks(documentId, kbId, chunks);

            if (isCancelled(jobId)) {
                log.info("[Job {}] Job cancelled during/after indexing; halting final completion", jobId);
                return;
            }

            // STEP 5: FINALIZATION & STATUS SYNCHRONIZATION
            knowledgeServiceClient.updateDocumentProcessingStatus(documentId, "COMPLETED");
            completeJobSuccess(jobId, String.format("Successfully indexed %d chunks", indexedCount));
            log.info("[Job {}] Pipeline successfully finished for document {}", jobId, documentId);

        } catch (DocumentNotFoundException e) {
            log.error("[Job {}] Document not found failure (non-retryable): {}", jobId, e.getMessage());
            handlePermanentFailure(jobId, documentId, "DOCUMENT_NOT_FOUND", e.getMessage());
        } catch (PermanentProcessingException e) {
            log.error("[Job {}] Permanent failure (non-retryable): [{}] {}", jobId, e.getErrorCode(), e.getMessage());
            handlePermanentFailure(jobId, documentId, e.getErrorCode(), e.getMessage());
        } catch (DownstreamServiceException e) {
            if (e.isRetryable()) {
                log.warn("[Job {}] Transient downstream failure (status {}): {}", jobId, e.getStatusCode(),
                        e.getMessage());
                handleTransientFailure(jobId, documentId, "DOWNSTREAM_ERROR", e.getMessage());
            } else {
                log.error("[Job {}] Permanent downstream client failure (status {}): {}", jobId, e.getStatusCode(),
                        e.getMessage());
                handlePermanentFailure(jobId, documentId, "DOWNSTREAM_CLIENT_ERROR", e.getMessage());
            }
        } catch (TransientProcessingException e) {
            log.warn("[Job {}] Transient failure: {}", jobId, e.getMessage());
            handleTransientFailure(jobId, documentId, "DOWNSTREAM_ERROR", e.getMessage());
        } catch (Exception e) {
            log.error("[Job {}] Unexpected execution error", jobId, e);
            handleTransientFailure(jobId, documentId, "UNEXPECTED_ERROR", e.getMessage());
        }
    }

    /**
     * PROC-13: Concurrency-safe atomic job claiming without Thread.sleep().
     * Executes a single atomic conditional update (WHERE status = 'QUEUED') with REQUIRES_NEW.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ProcessingJob> claimJobForExecution(UUID jobId) {
        try {
            Instant now = Instant.now();
            int updated = repository.claimJobAtomic(
                    jobId,
                    ProcessingStatus.QUEUED,
                    ProcessingStatus.VALIDATING,
                    ProcessingStage.VALIDATION,
                    "Validating document eligibility",
                    now);

            if (updated == 0) {
                log.debug("Job {} cannot be claimed; either not found or not in QUEUED status", jobId);
                return Optional.empty();
            }

            Optional<ProcessingJob> optJob = repository.findById(jobId);
            optJob.ifPresent(job -> {
                if (job.getStatus() == ProcessingStatus.QUEUED) {
                    job.setStatus(ProcessingStatus.VALIDATING);
                    job.setStage(ProcessingStage.VALIDATION);
                    job.setCurrentStep("Validating document eligibility");
                    job.setAttemptCount(job.getAttemptCount() + 1);
                    if (job.getStartedAt() == null) {
                        job.setStartedAt(now);
                    }
                    job.setUpdatedAt(now);
                }
            });
            return optJob;
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException
                | jakarta.persistence.OptimisticLockException e) {
            log.warn("Optimistic lock conflict when claiming job {}; another worker claimed it", jobId);
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncKnowledgeBaseId(UUID jobId, UUID kbId) {
        repository.findById(jobId).ifPresent(job -> {
            job.setKnowledgeBaseId(kbId);
            repository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transitionStep(UUID jobId, ProcessingStatus status, ProcessingStage stage, String step) {
        Optional<ProcessingJob> optJob = repository.findById(jobId);
        if (optJob.isEmpty())
            return false;
        ProcessingJob job = optJob.get();
        if (job.getStatus() == ProcessingStatus.CANCELLED) {
            return false;
        }
        job.setStatus(status);
        job.setStage(stage);
        job.setCurrentStep(step);
        repository.save(job);
        return true;
    }

    public boolean isCancelled(UUID jobId) {
        return repository.findById(jobId)
                .map(j -> j.getStatus() == ProcessingStatus.CANCELLED)
                .orElse(true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeJobSuccess(UUID jobId, String stepMessage) {
        repository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == ProcessingStatus.CANCELLED) {
                log.info("[Job {}] Job was cancelled; not marking COMPLETED", jobId);
                return;
            }
            job.setStatus(ProcessingStatus.COMPLETED);
            job.setStage(ProcessingStage.COMPLETED);
            job.setCurrentStep(stepMessage);
            job.setCompletedAt(Instant.now());
            job.setErrorCode(null);
            job.setErrorMessage(null);
            repository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePermanentFailure(UUID jobId, UUID documentId, String errorCode, String errorMessage) {
        repository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == ProcessingStatus.CANCELLED) {
                log.info("[Job {}] Job was cancelled; not marking FAILED", jobId);
                return;
            }
            job.setStatus(ProcessingStatus.FAILED);
            job.setStage(ProcessingStage.FAILED);
            job.setErrorCode(errorCode);
            job.setErrorMessage(errorMessage);
            job.setCurrentStep("Permanent failure: " + errorCode);
            job.setCompletedAt(Instant.now());
            repository.save(job);
        });
        if (!isCancelled(jobId)) {
            knowledgeServiceClient.updateDocumentProcessingStatus(documentId, "FAILED");
        }
    }

    /**
     * PROC-03 Scope A: Delayed retry handling with bounded exponential backoff.
     * When attempts remain, transitions job to QUEUED in database, calculates backoff delay,
     * and schedules delayed retry submission strictly after transaction commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTransientFailure(UUID jobId, UUID documentId, String errorCode, String errorMessage) {
        Optional<ProcessingJob> optJob = repository.findById(jobId);
        if (optJob.isEmpty()) {
            return;
        }
        ProcessingJob job = optJob.get();
        if (job.getStatus() == ProcessingStatus.CANCELLED) {
            log.info("[Job {}] Job was cancelled; ignoring transient failure", jobId);
            return;
        }

        if (job.getAttemptCount() < job.getMaxAttempts()) {
            long delayMs = calculateRetryDelayMs(job.getAttemptCount());
            log.info("[Job {}] Transient failure on attempt {}/{}. Scheduling retry in {} ms.",
                    jobId, job.getAttemptCount(), job.getMaxAttempts(), delayMs);
            job.setStatus(ProcessingStatus.QUEUED);
            job.setStage(ProcessingStage.INITIALIZATION);
            job.setCurrentStep(String.format("Awaiting retry (%d ms backoff) after transient failure: %s", delayMs, errorMessage));
            job.setErrorCode(errorCode);
            job.setErrorMessage(errorMessage);
            repository.save(job);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        scheduleDelayedRetry(jobId, delayMs);
                    }
                });
            } else {
                scheduleDelayedRetry(jobId, delayMs);
            }
        } else {
            log.error("[Job {}] Exhausted all {} retry attempts. Marking FAILED.", jobId, job.getMaxAttempts());
            job.setStatus(ProcessingStatus.FAILED);
            job.setStage(ProcessingStage.FAILED);
            job.setErrorCode("MAX_RETRIES_EXCEEDED");
            job.setErrorMessage("Max retry attempts reached. Last error: " + errorMessage);
            job.setCurrentStep("Job failed: maximum retry attempts exceeded");
            job.setCompletedAt(Instant.now());
            repository.save(job);
            knowledgeServiceClient.updateDocumentProcessingStatus(documentId, "FAILED");
        }
    }
}
