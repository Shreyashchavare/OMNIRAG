package com.omragul.processing.service;

import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
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
import com.omragul.processing.security.UserSecurityContext;
import com.omragul.processing.security.UserSecurityContextHolder;
import com.omragul.processing.service.impl.ProcessingJobServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {

    @Mock
    private ProcessingJobRepository repository;

    @Mock
    private ProcessingOrchestrator orchestrator;

    @Mock
    private KnowledgeServiceClient knowledgeServiceClient;

    private ProcessingJobService service;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        UserSecurityContextHolder.setContext(UserSecurityContext.user(testUserId, Set.of("USER")));
        lenient().when(knowledgeServiceClient.verifyDocumentWriteAccess(any(), any())).thenReturn(true);
        service = new ProcessingJobServiceImpl(repository, orchestrator, knowledgeServiceClient, 3);
    }

    @AfterEach
    void tearDown() {
        UserSecurityContextHolder.clear();
    }

    @Test
    @DisplayName("Should create and enqueue a new processing job")
    void testCreateJobSuccess() {
        UUID docId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(kbId)
                .build();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(kbId)
                .ownerId(testUserId)
                .build()));

        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());

        when(repository.saveAndFlush(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            job.setId(jobId);
            job.setCreatedAt(Instant.now());
            job.setUpdatedAt(Instant.now());
            return job;
        });

        ProcessingJobResponse response = service.createJob(request);

        assertNotNull(response);
        assertEquals(jobId, response.getId());
        assertEquals(docId, response.getDocumentId());
        assertEquals(kbId, response.getKnowledgeBaseId());
        assertEquals(testUserId, response.getOwnerId());
        assertEquals(ProcessingStatus.QUEUED, response.getStatus());

        verify(orchestrator, times(1)).submitJob(jobId);
    }

    @Test
    @DisplayName("Should return existing job for identical idempotency key")
    void testCreateJobIdempotency() {
        UUID docId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        String idemKey = "idem-key-123";

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(kbId)
                .ownerId(testUserId)
                .build()));

        ProcessingJob existing = ProcessingJob.builder()
                .id(jobId)
                .documentId(docId)
                .knowledgeBaseId(kbId)
                .ownerId(testUserId)
                .status(ProcessingStatus.VALIDATING)
                .idempotencyKey(idemKey)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findByDocumentIdAndIdempotencyKey(docId, idemKey)).thenReturn(Optional.of(existing));

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(kbId)
                .idempotencyKey(idemKey)
                .build();

        ProcessingJobResponse response = service.createJob(request);

        assertEquals(jobId, response.getId());
        assertEquals(ProcessingStatus.VALIDATING, response.getStatus());
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
        verify(orchestrator, never()).submitJob(any());
    }

    @Test
    @DisplayName("Should translate concurrent DataIntegrityViolationException on saveAndFlush into DuplicateJobException")
    void testCreateJobConcurrentDataIntegrityViolation() {
        UUID docId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(kbId)
                .idempotencyKey("concurrent-key")
                .build();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(kbId)
                .ownerId(testUserId)
                .build()));

        when(repository.findByDocumentIdAndIdempotencyKey(eq(docId), eq("concurrent-key")))
                .thenReturn(Optional.empty());
        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ProcessingJob.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key violation"));

        DuplicateJobException ex = assertThrows(DuplicateJobException.class, () -> service.createJob(request));
        assertTrue(ex.getMessage().contains("already exists or is being created"));
    }

    @Test
    @DisplayName("Should reject job creation if document is already actively processing")
    void testCreateJobDuplicateRejection() {
        UUID docId = UUID.randomUUID();
        UUID activeJobId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(kbId)
                .ownerId(testUserId)
                .build()));

        ProcessingJob activeJob = ProcessingJob.builder()
                .id(activeJobId)
                .documentId(docId)
                .knowledgeBaseId(kbId)
                .ownerId(testUserId)
                .status(ProcessingStatus.EXTRACTING)
                .build();

        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.of(activeJob));

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(kbId)
                .build();

        DuplicateJobException ex = assertThrows(DuplicateJobException.class, () -> service.createJob(request));
        assertEquals(activeJobId, ex.getExistingJobId());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should retrieve job status by ID")
    void testGetJobStatus() {
        UUID jobId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(docId)
                .ownerId(testUserId)
                .status(ProcessingStatus.CHUNKING)
                .stage(ProcessingStage.CHUNKING)
                .currentStep("Splitting into chunks")
                .attemptCount(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        JobStatusResponse statusResponse = service.getJobStatus(jobId);
        assertNotNull(statusResponse);
        assertEquals(jobId, statusResponse.getId());
        assertEquals(ProcessingStatus.CHUNKING, statusResponse.getStatus());
        assertEquals("Splitting into chunks", statusResponse.getCurrentStep());
    }

    @Test
    @DisplayName("Should throw JobNotFoundException when job does not exist")
    void testGetJobNotFound() {
        UUID jobId = UUID.randomUUID();
        when(repository.findById(jobId)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> service.getJobById(jobId));
    }

    @Test
    @DisplayName("Should allow retrying a failed job under max attempts")
    void testRetryJobSuccess() {
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(UUID.randomUUID())
                .ownerId(testUserId)
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .errorCode("DOWNSTREAM_TIMEOUT")
                .errorMessage("Read timed out")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProcessingJobResponse retried = service.retryJob(jobId);
        assertEquals(ProcessingStatus.QUEUED, retried.getStatus());
        assertNull(retried.getErrorCode());
        verify(orchestrator, times(1)).submitJob(jobId);
    }

    @Test
    @DisplayName("Should reject retry when job reached max attempts")
    void testRetryJobMaxAttemptsExceeded() {
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(UUID.randomUUID())
                .ownerId(testUserId)
                .status(ProcessingStatus.FAILED)
                .attemptCount(3)
                .maxAttempts(3)
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(MaxRetriesExceededException.class, () -> service.retryJob(jobId));
        verify(orchestrator, never()).submitJob(any());
    }

    @Test
    @DisplayName("Should cancel an active job")
    void testCancelJobSuccess() {
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(UUID.randomUUID())
                .ownerId(testUserId)
                .status(ProcessingStatus.VALIDATING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProcessingJobResponse cancelled = service.cancelJob(jobId);
        assertEquals(ProcessingStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCompletedAt());
    }

    @Test
    @DisplayName("Should reject cancellation of completed job")
    void testCancelCompletedJobRejected() {
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(UUID.randomUUID())
                .ownerId(testUserId)
                .status(ProcessingStatus.COMPLETED)
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateTransitionException.class, () -> service.cancelJob(jobId));
    }

    @Test
    @DisplayName("PROC-08: retryJob dispatches task submission strictly after transaction commit")
    void testRetryJobSubmittedOnlyAfterTransactionCommit() {
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(UUID.randomUUID())
                .ownerId(testUserId)
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            ProcessingJobResponse response = service.retryJob(jobId);
            assertNotNull(response);
            assertEquals(ProcessingStatus.QUEUED, response.getStatus());

            // Task must NOT be submitted before commit
            verify(orchestrator, never()).submitJob(jobId);

            // Trigger afterCommit
            for (org.springframework.transaction.support.TransactionSynchronization sync :
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            // Task must be submitted after commit
            verify(orchestrator, times(1)).submitJob(jobId);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("PROC-08: retryJob does NOT dispatch task submission when transaction rolls back")
    void testRetryJobNotSubmittedWhenTransactionRollsBack() {
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(UUID.randomUUID())
                .ownerId(testUserId)
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            ProcessingJobResponse response = service.retryJob(jobId);
            assertNotNull(response);

            // Simulate rollback
            for (org.springframework.transaction.support.TransactionSynchronization sync :
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            }

            // Task must NEVER be submitted if transaction rolled back
            verify(orchestrator, never()).submitJob(jobId);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("PROC-08: retryJob handles executor rejection in afterCommit safely without unhandled exception")
    void testRetryJobExecutorRejectionHandledSafely() {
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(UUID.randomUUID())
                .ownerId(testUserId)
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new java.util.concurrent.RejectedExecutionException("Worker queue full"))
                .when(orchestrator).submitJob(jobId);

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            ProcessingJobResponse response = service.retryJob(jobId);
            assertNotNull(response);
            assertEquals(ProcessingStatus.QUEUED, response.getStatus());

            // afterCommit catches rejection safely
            assertDoesNotThrow(() -> {
                for (org.springframework.transaction.support.TransactionSynchronization sync :
                        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                    sync.afterCommit();
                }
            });
            verify(orchestrator, times(1)).submitJob(jobId);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
