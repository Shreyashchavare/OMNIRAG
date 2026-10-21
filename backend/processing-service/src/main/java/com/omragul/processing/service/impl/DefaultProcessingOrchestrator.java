package com.omragul.processing.service.impl;

import com.omragul.processing.client.EmbeddingServiceClient;
import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.MultimodalServiceClient;
import com.omragul.processing.client.RagServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.DownstreamServiceException;
import com.omragul.processing.exception.PermanentProcessingException;
import com.omragul.processing.exception.TransientProcessingException;
import com.omragul.processing.repository.ProcessingJobRepository;
import com.omragul.processing.service.ProcessingOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultProcessingOrchestrator implements ProcessingOrchestrator {

    private final ProcessingJobRepository repository;
    private final KnowledgeServiceClient knowledgeServiceClient;
    private final MultimodalServiceClient multimodalServiceClient;
    private final EmbeddingServiceClient embeddingServiceClient;
    private final RagServiceClient ragServiceClient;

    @Qualifier("processingTaskExecutor")
    private final Executor taskExecutor;

    @Override
    public void submitJob(UUID jobId) {
        log.info("Submitting job {} to async processing worker pool", jobId);
        taskExecutor.execute(() -> processJobPipeline(jobId));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        log.info("Checking for interrupted processing jobs needing recovery after startup...");
        List<ProcessingJob> interruptedJobs = repository.findByStatusIn(List.of(
                ProcessingStatus.QUEUED,
                ProcessingStatus.VALIDATING,
                ProcessingStatus.EXTRACTING,
                ProcessingStatus.CHUNKING,
                ProcessingStatus.EMBEDDING_INDEXING
        ));

        if (interruptedJobs.isEmpty()) {
            log.info("No interrupted processing jobs found. System is clean.");
            return;
        }

        log.info("Found {} interrupted job(s) for startup recovery", interruptedJobs.size());
        for (ProcessingJob job : interruptedJobs) {
            try {
                recoverSingleJob(job.getId());
            } catch (Exception e) {
                log.error("Failed to recover job {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void recoverSingleJob(UUID jobId) {
        Optional<ProcessingJob> optJob = repository.findById(jobId);
        if (optJob.isEmpty()) return;

        ProcessingJob job = optJob.get();
        if (job.getStatus().isTerminal()) return;

        if (job.getAttemptCount() < job.getMaxAttempts()) {
            log.info("Recovering in-flight job {} (attempt {}/{}), re-queueing",
                    jobId, job.getAttemptCount(), job.getMaxAttempts());
            job.setStatus(ProcessingStatus.QUEUED);
            job.setStage(ProcessingStage.INITIALIZATION);
            job.setCurrentStep("Recovered after service restart; queued for execution");
            repository.save(job);
            submitJob(jobId);
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
                throw new PermanentProcessingException("DOCUMENT_NOT_FOUND", "Document not found in Knowledge Service: " + documentId);
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
            if (!transitionStep(jobId, ProcessingStatus.EXTRACTING, ProcessingStage.EXTRACTION, "Extracting text/multimodal content")) {
                log.info("[Job {}] Job cancelled before extraction; halting pipeline", jobId);
                return;
            }

            log.info("[Job {}] Step 2: Extracting text from document {}", jobId, documentId);
            String extractedText = multimodalServiceClient.extractText(doc);

            if (extractedText == null || extractedText.isBlank()) {
                throw new PermanentProcessingException("EMPTY_TEXT", "No text content could be extracted from document");
            }

            if (isCancelled(jobId)) {
                log.info("[Job {}] Job cancelled after extraction; halting pipeline", jobId);
                return;
            }

            // STEP 3: CHUNKING (Multimodal Chunk Service)
            if (!transitionStep(jobId, ProcessingStatus.CHUNKING, ProcessingStage.CHUNKING, "Splitting content into text chunks")) {
                log.info("[Job {}] Job cancelled before chunking; halting pipeline", jobId);
                return;
            }

            log.info("[Job {}] Step 3: Chunking content for document {}", jobId, documentId);
            List<String> chunks = multimodalServiceClient.createChunks(extractedText);

            if (chunks.isEmpty()) {
                throw new PermanentProcessingException("EMPTY_CHUNKS", "Chunking produced 0 chunks from extracted text");
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

            log.info("[Job {}] Step 4: Indexing {} chunks via RAG Service for document {}", jobId, chunks.size(), documentId);
            int indexedCount = ragServiceClient.indexChunks(documentId, kbId, chunks);

            if (isCancelled(jobId)) {
                log.info("[Job {}] Job cancelled during/after indexing; halting final completion", jobId);
                return;
            }

            // STEP 5: FINALIZATION & STATUS SYNCHRONIZATION
            knowledgeServiceClient.updateDocumentProcessingStatus(documentId, "COMPLETED");
            completeJobSuccess(jobId, String.format("Successfully indexed %d chunks", indexedCount));
            log.info("[Job {}] Pipeline successfully finished for document {}", jobId, documentId);

        } catch (PermanentProcessingException e) {
            log.error("[Job {}] Permanent failure (non-retryable): [{}] {}", jobId, e.getErrorCode(), e.getMessage());
            handlePermanentFailure(jobId, documentId, e.getErrorCode(), e.getMessage());
        } catch (DownstreamServiceException | TransientProcessingException e) {
            log.warn("[Job {}] Transient failure: {}", jobId, e.getMessage());
            handleTransientFailure(jobId, documentId, "DOWNSTREAM_ERROR", e.getMessage());
        } catch (Exception e) {
            log.error("[Job {}] Unexpected execution error", jobId, e);
            handleTransientFailure(jobId, documentId, "UNEXPECTED_ERROR", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ProcessingJob> claimJobForExecution(UUID jobId) {
        try {
            Optional<ProcessingJob> optJob = repository.findById(jobId);
            for (int i = 0; i < 5 && optJob.isEmpty(); i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                optJob = repository.findById(jobId);
            }
            if (optJob.isEmpty()) return Optional.empty();

            ProcessingJob job = optJob.get();
            if (job.getStatus() != ProcessingStatus.QUEUED) {
                log.debug("Job {} cannot be claimed; current status is {}", jobId, job.getStatus());
                return Optional.empty();
            }

            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setStatus(ProcessingStatus.VALIDATING);
            job.setStage(ProcessingStage.VALIDATION);
            job.setCurrentStep("Validating document eligibility");
            if (job.getStartedAt() == null) {
                job.setStartedAt(Instant.now());
            }

            ProcessingJob saved = repository.save(job);
            return Optional.of(saved);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
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
        if (optJob.isEmpty()) return false;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTransientFailure(UUID jobId, UUID documentId, String errorCode, String errorMessage) {
        repository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == ProcessingStatus.CANCELLED) {
                log.info("[Job {}] Job was cancelled; ignoring transient failure", jobId);
                return;
            }
            if (job.getAttemptCount() < job.getMaxAttempts()) {
                log.info("[Job {}] Transient failure on attempt {}/{}. Re-queueing for retry.",
                        jobId, job.getAttemptCount(), job.getMaxAttempts());
                job.setStatus(ProcessingStatus.QUEUED);
                job.setStage(ProcessingStage.INITIALIZATION);
                job.setCurrentStep("Awaiting retry after transient failure: " + errorMessage);
                job.setErrorCode(errorCode);
                job.setErrorMessage(errorMessage);
                repository.save(job);
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
        });
    }
}
