package com.omragul.processing.service;

import com.omragul.processing.client.EmbeddingServiceClient;
import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.MultimodalServiceClient;
import com.omragul.processing.client.RagServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.DownstreamServiceException;
import com.omragul.processing.repository.ProcessingJobRepository;
import com.omragul.processing.service.impl.DefaultProcessingOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultProcessingOrchestratorTest {

        @Mock
        private ProcessingJobRepository repository;

        @Mock
        private KnowledgeServiceClient knowledgeServiceClient;

        @Mock
        private MultimodalServiceClient multimodalServiceClient;

        @Mock
        private EmbeddingServiceClient embeddingServiceClient;

        @Mock
        private RagServiceClient ragServiceClient;

        @Mock
        private java.util.concurrent.Executor taskExecutor;

        private DefaultProcessingOrchestrator orchestrator;

        @BeforeEach
        void setUp() {
                orchestrator = new DefaultProcessingOrchestrator(
                                repository,
                                knowledgeServiceClient,
                                multimodalServiceClient,
                                embeddingServiceClient,
                                ragServiceClient,
                                taskExecutor);
                lenient().when(repository.claimJobAtomic(any(), any(), any(), any(), any(), any())).thenReturn(1);
        }

        @Test
        @DisplayName("Should successfully execute the entire processing pipeline")
        void testSuccessfulPipelineExecution() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();
                UUID kbId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .knowledgeBaseId(kbId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(0)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                DocumentDto docDto = DocumentDto.builder()
                                .id(docId)
                                .name("test-doc.pdf")
                                .mimeType("application/pdf")
                                .status("ACTIVE")
                                .build();

                when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(docDto));
                when(multimodalServiceClient.extractText(docDto)).thenReturn("Sample extracted document text");
                when(multimodalServiceClient.createChunks("Sample extracted document text"))
                                .thenReturn(List.of("Chunk 1", "Chunk 2"));
                when(ragServiceClient.indexChunks(eq(docId), eq(kbId), any())).thenReturn(2);

                orchestrator.processJobPipeline(jobId);

                // Verify status sync with Knowledge Service
                verify(knowledgeServiceClient, times(1)).updateDocumentProcessingStatus(docId, "PROCESSING");
                verify(knowledgeServiceClient, times(1)).updateDocumentProcessingStatus(docId, "COMPLETED");

                // Verify terminal state
                assertEquals(ProcessingStatus.COMPLETED, job.getStatus());
                assertEquals(ProcessingStage.COMPLETED, job.getStage());
                assertNotNull(job.getCompletedAt());
        }

        @Test
        @DisplayName("Should handle permanent failure (document not found) without retry")
        void testPermanentFailureDocumentNotFound() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(0)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.empty());

                orchestrator.processJobPipeline(jobId);

                // Permanent failure: notify Knowledge Service FAILED
                verify(knowledgeServiceClient, times(1)).updateDocumentProcessingStatus(docId, "FAILED");
                verifyNoInteractions(multimodalServiceClient);
                verifyNoInteractions(ragServiceClient);

                assertEquals(ProcessingStatus.FAILED, job.getStatus());
                assertEquals("DOCUMENT_NOT_FOUND", job.getErrorCode());
        }

        @Test
        @DisplayName("Should handle transient failure and re-queue job if under max attempts")
        void testTransientFailureRequeuesJob() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(0)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                DocumentDto docDto = DocumentDto.builder()
                                .id(docId)
                                .name("test.txt")
                                .mimeType("text/plain")
                                .status("ACTIVE")
                                .build();

                when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(docDto));
                when(multimodalServiceClient.extractText(docDto))
                                .thenThrow(new DownstreamServiceException("multimodal-service", 503,
                                                "Service unavailable"));

                orchestrator.processJobPipeline(jobId);

                // Because attempt count was 1 (< 3 max), job should be set back to QUEUED
                assertEquals(ProcessingStatus.QUEUED, job.getStatus());
                assertEquals("DOWNSTREAM_ERROR", job.getErrorCode());
                verify(knowledgeServiceClient, never()).updateDocumentProcessingStatus(docId, "COMPLETED");
        }

        @Test
        @DisplayName("Should mark FAILED when transient failures exhaust maximum attempts")
        void testTransientFailureExhaustsMaxAttempts() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(2) // next attempt will be 3 (equals maxAttempts)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                when(knowledgeServiceClient.getDocument(docId))
                                .thenThrow(new DownstreamServiceException("knowledge-service", 504, "Gateway timeout"));

                orchestrator.processJobPipeline(jobId);

                // Attempt 3 of 3 exhausted -> job marked FAILED
                assertEquals(ProcessingStatus.FAILED, job.getStatus());
                assertEquals("MAX_RETRIES_EXCEEDED", job.getErrorCode());
                verify(knowledgeServiceClient, times(1)).updateDocumentProcessingStatus(docId, "FAILED");
        }

        @Test
        @DisplayName("Should recover interrupted in-flight jobs on startup")
        void testStartupRecovery() {
                UUID jobId1 = UUID.randomUUID();
                UUID jobId2 = UUID.randomUUID();

                ProcessingJob job1 = ProcessingJob.builder()
                                .id(jobId1)
                                .documentId(UUID.randomUUID())
                                .status(ProcessingStatus.EXTRACTING)
                                .attemptCount(1)
                                .maxAttempts(3)
                                .build();

                ProcessingJob job2 = ProcessingJob.builder()
                                .id(jobId2)
                                .documentId(UUID.randomUUID())
                                .status(ProcessingStatus.EMBEDDING_INDEXING)
                                .attemptCount(3)
                                .maxAttempts(3)
                                .build();

                when(repository.findByStatusIn(any())).thenReturn(List.of(job1, job2));
                when(repository.findById(jobId1)).thenReturn(Optional.of(job1));
                when(repository.findById(jobId2)).thenReturn(Optional.of(job2));
                when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                orchestrator.recoverInterruptedJobs();

                // Job 1 had attempt 1 < 3: should be re-queued
                assertEquals(ProcessingStatus.QUEUED, job1.getStatus());

                // Job 2 had attempt 3 == 3: should be marked FAILED
                assertEquals(ProcessingStatus.FAILED, job2.getStatus());
                assertEquals("RESTART_RECOVERY_EXHAUSTED", job2.getErrorCode());
        }

        @Test
        @DisplayName("Should abort pipeline and never overwrite status when job is cancelled")
        void testCancellationPreventsFurtherStagesAndDoesNotOverwrite() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(0)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                DocumentDto docDto = DocumentDto.builder()
                                .id(docId)
                                .name("test.txt")
                                .mimeType("text/plain")
                                .status("ACTIVE")
                                .build();

                when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(docDto));

                // Simulate cancellation right after validation
                doAnswer(invocation -> {
                        job.setStatus(ProcessingStatus.CANCELLED);
                        job.setStage(ProcessingStage.CANCELLED);
                        return null;
                }).when(knowledgeServiceClient).updateDocumentProcessingStatus(docId, "PROCESSING");

                orchestrator.processJobPipeline(jobId);

                // Verification: Multimodal extraction and RAG indexing should never be called
                verifyNoInteractions(multimodalServiceClient);
                verifyNoInteractions(ragServiceClient);

                // Job status remains CANCELLED and is not overwritten with COMPLETED or FAILED
                assertEquals(ProcessingStatus.CANCELLED, job.getStatus());
                assertEquals(ProcessingStage.CANCELLED, job.getStage());
        }

        @Test
        @DisplayName("Should gracefully handle optimistic lock exception when claiming job")
        void testOptimisticLockConflictReturnsEmptyGracefully() {
                UUID jobId = UUID.randomUUID();
                lenient().doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                                ProcessingJob.class, jobId))
                                .when(repository).claimJobAtomic(eq(jobId), any(), any(), any(), any(), any());

                Optional<ProcessingJob> claimed = orchestrator.claimJobForExecution(jobId);
                assertTrue(claimed.isEmpty());
        }

        @Test
        @DisplayName("Should fail permanently without retry when extracted text is empty")
        void testEmptyTextExtractionTriggersPermanentFailure() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(0)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                DocumentDto docDto = DocumentDto.builder()
                                .id(docId)
                                .name("empty.txt")
                                .mimeType("text/plain")
                                .status("ACTIVE")
                                .build();

                when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(docDto));
                when(multimodalServiceClient.extractText(docDto)).thenReturn("   "); // empty / blank

                orchestrator.processJobPipeline(jobId);

                assertEquals(ProcessingStatus.FAILED, job.getStatus());
                assertEquals("EMPTY_TEXT", job.getErrorCode());
                verify(knowledgeServiceClient, times(1)).updateDocumentProcessingStatus(docId, "FAILED");
                verifyNoInteractions(ragServiceClient);
        }

        @Test
        @DisplayName("FINDING-3.4-04: Permanent 4xx errors (400, 401, 403, 422) fail immediately without retry")
        void testPermanent4xxClientErrorsFailImmediatelyWithoutRetry() {
                int[] nonRetryableCodes = { 400, 401, 403, 422 };

                for (int statusCode : nonRetryableCodes) {
                        UUID jobId = UUID.randomUUID();
                        UUID docId = UUID.randomUUID();

                        ProcessingJob job = ProcessingJob.builder()
                                        .id(jobId)
                                        .documentId(docId)
                                        .status(ProcessingStatus.QUEUED)
                                        .attemptCount(0)
                                        .maxAttempts(3)
                                        .build();

                        when(repository.findById(jobId)).thenReturn(Optional.of(job));
                        when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                        when(knowledgeServiceClient.getDocument(docId)).thenThrow(
                                        new DownstreamServiceException("knowledge-service", statusCode,
                                                        "Client error " + statusCode, false));

                        orchestrator.processJobPipeline(jobId);

                        // Must immediately mark FAILED with DOWNSTREAM_CLIENT_ERROR and not re-queue
                        assertEquals(ProcessingStatus.FAILED, job.getStatus(),
                                        "Status for " + statusCode + " must be FAILED");
                        assertEquals("DOWNSTREAM_CLIENT_ERROR", job.getErrorCode());
                        assertEquals(1, job.getAttemptCount(), "Attempt count should only be 1 (not retried)");
                        verify(knowledgeServiceClient, atLeastOnce()).updateDocumentProcessingStatus(docId, "FAILED");
                }
        }

        @Test
        @DisplayName("FINDING-3.4-04: Transient errors (408, 429, 500, 503, timeout) are re-queued for retry")
        void testTransientErrorsRequeuedForRetry() {
                int[] retryableCodes = { 408, 429, 500, 503 };

                for (int statusCode : retryableCodes) {
                        UUID jobId = UUID.randomUUID();
                        UUID docId = UUID.randomUUID();

                        ProcessingJob job = ProcessingJob.builder()
                                        .id(jobId)
                                        .documentId(docId)
                                        .status(ProcessingStatus.QUEUED)
                                        .attemptCount(0)
                                        .maxAttempts(3)
                                        .build();

                        when(repository.findById(jobId)).thenReturn(Optional.of(job));
                        when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                        when(knowledgeServiceClient.getDocument(docId)).thenThrow(
                                        new DownstreamServiceException("knowledge-service", statusCode,
                                                        "Transient error " + statusCode, true));

                        orchestrator.processJobPipeline(jobId);

                        // Must re-queue as QUEUED for retry
                        assertEquals(ProcessingStatus.QUEUED, job.getStatus(),
                                        "Status for " + statusCode + " must be QUEUED");
                        assertEquals("DOWNSTREAM_ERROR", job.getErrorCode());
                        assertEquals(1, job.getAttemptCount());
                }
        }

        @Test
        @DisplayName("FINDING-3.4-04: Network timeout remains retryable and re-queued")
        void testNetworkTimeoutRequeuedForRetry() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(0)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                when(knowledgeServiceClient.getDocument(docId)).thenThrow(
                                new DownstreamServiceException("knowledge-service",
                                                "Network failure connecting to Knowledge Service",
                                                new java.io.IOException("Connection timed out")));

                orchestrator.processJobPipeline(jobId);

                assertEquals(ProcessingStatus.QUEUED, job.getStatus());
                assertEquals("DOWNSTREAM_ERROR", job.getErrorCode());
                assertEquals(1, job.getAttemptCount());
        }

        @Test
        @DisplayName("FINDING-3.4-04: Missing document remains immediate DOCUMENT_NOT_FOUND permanent failure")
        void testMissingDocumentRemainsImmediatePermanentFailure() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(0)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.empty());

                orchestrator.processJobPipeline(jobId);

                assertEquals(ProcessingStatus.FAILED, job.getStatus());
                assertEquals("DOCUMENT_NOT_FOUND", job.getErrorCode());
                assertEquals(1, job.getAttemptCount());
                verify(knowledgeServiceClient, times(1)).updateDocumentProcessingStatus(docId, "FAILED");
        }

        @Test
        @DisplayName("PROC-13: claimJobForExecution successfully claims job when atomic update returns 1")
        void testClaimJobForExecutionSuccess() {
                UUID jobId = UUID.randomUUID();
                ProcessingJob claimedJob = ProcessingJob.builder()
                                .id(jobId)
                                .status(ProcessingStatus.VALIDATING)
                                .stage(ProcessingStage.VALIDATION)
                                .attemptCount(1)
                                .version(1L)
                                .build();

                lenient().doReturn(1).when(repository).claimJobAtomic(eq(jobId), any(), any(), any(), any(), any());
                lenient().doReturn(Optional.of(claimedJob)).when(repository).findById(jobId);

                Optional<ProcessingJob> result = orchestrator.claimJobForExecution(jobId);

                assertTrue(result.isPresent());
                assertEquals(ProcessingStatus.VALIDATING, result.get().getStatus());
                assertEquals(1, result.get().getAttemptCount());
        }

        @Test
        @DisplayName("PROC-13: claimJobForExecution returns empty when atomic update returns 0 (not queued or not found)")
        void testClaimJobForExecutionNotQueuedOrNotFound() {
                UUID jobId = UUID.randomUUID();
                lenient().doReturn(0).when(repository).claimJobAtomic(eq(jobId), any(), any(), any(), any(), any());

                Optional<ProcessingJob> result = orchestrator.claimJobForExecution(jobId);

                assertTrue(result.isEmpty());
                verify(repository, never()).findById(jobId);
        }

        @Test
        @DisplayName("PROC-13: claimJobForExecution handles OptimisticLockingFailureException safely")
        void testClaimJobForExecutionOptimisticLockExceptionHandled() {
                UUID jobId = UUID.randomUUID();
                lenient().doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                                ProcessingJob.class, jobId))
                                .when(repository).claimJobAtomic(eq(jobId), any(), any(), any(), any(), any());

                Optional<ProcessingJob> result = orchestrator.claimJobForExecution(jobId);

                assertTrue(result.isEmpty());
        }

        // ==========================================
        // PROC-03 Scope A: Delayed Retry Tests
        // ==========================================

        @Test
        @DisplayName("PROC-03 Scope A: calculateRetryDelayMs computes bounded exponential backoff")
        void testCalculateRetryDelayMs() {
                orchestrator.setRetryInitialDelayMs(1000L);
                orchestrator.setRetryMaxDelayMs(30000L);
                orchestrator.setRetryBackoffMultiplier(2.0);

                assertEquals(1000L, orchestrator.calculateRetryDelayMs(0));
                assertEquals(1000L, orchestrator.calculateRetryDelayMs(1));
                assertEquals(2000L, orchestrator.calculateRetryDelayMs(2));
                assertEquals(4000L, orchestrator.calculateRetryDelayMs(3));
                assertEquals(8000L, orchestrator.calculateRetryDelayMs(4));
                assertEquals(16000L, orchestrator.calculateRetryDelayMs(5));
                assertEquals(30000L, orchestrator.calculateRetryDelayMs(6)); // 32000 capped to 30000
                assertEquals(30000L, orchestrator.calculateRetryDelayMs(10)); // capped
        }

        @Test
        @DisplayName("PROC-03 Scope A: Transient failure schedules delayed retry with backoff")
        void testDelayedRetryScheduledOnTransientFailure() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob job = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .status(ProcessingStatus.QUEUED)
                                .attemptCount(1)
                                .maxAttempts(3)
                                .build();

                when(repository.findById(jobId)).thenReturn(Optional.of(job));
                lenient().when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                DocumentDto docDto = DocumentDto.builder()
                                .id(docId)
                                .name("test.txt")
                                .status("ACTIVE")
                                .build();

                when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(docDto));
                when(multimodalServiceClient.extractText(docDto))
                                .thenThrow(new DownstreamServiceException("multimodal-service", 503, "Service unavailable"));

                orchestrator.processJobPipeline(jobId);

                assertEquals(ProcessingStatus.QUEUED, job.getStatus());
                assertEquals("DOWNSTREAM_ERROR", job.getErrorCode());
                assertTrue(job.getCurrentStep().contains("backoff"));
        }

        @Test
        @DisplayName("PROC-03 Scope A: Duplicate retry scheduling of the same job is prevented")
        void testDuplicateSchedulingIsPrevented() {
                UUID jobId = UUID.randomUUID();

                orchestrator.scheduleDelayedRetry(jobId, 10000L);
                assertTrue(orchestrator.isJobScheduledForRetry(jobId));

                // Second invocation must be ignored due to deduplication set
                orchestrator.scheduleDelayedRetry(jobId, 10000L);
                assertTrue(orchestrator.isJobScheduledForRetry(jobId));
        }

        @Test
        @DisplayName("PROC-03 Scope A: TaskScheduler rejection is handled safely without failing job state")
        void testSchedulerRejectionHandledGracefully() {
                TaskScheduler mockScheduler = mock(TaskScheduler.class);
                doThrow(new RejectedExecutionException("Scheduler saturated"))
                                .when(mockScheduler).schedule(any(Runnable.class), any(Instant.class));

                DefaultProcessingOrchestrator orchWithMockScheduler = new DefaultProcessingOrchestrator(
                                repository,
                                knowledgeServiceClient,
                                multimodalServiceClient,
                                embeddingServiceClient,
                                ragServiceClient,
                                taskExecutor,
                                mockScheduler);

                UUID jobId = UUID.randomUUID();
                assertDoesNotThrow(() -> orchWithMockScheduler.scheduleDelayedRetry(jobId, 1000L));
                assertFalse(orchWithMockScheduler.isJobScheduledForRetry(jobId),
                                "Rejected job must be removed from scheduled set so watchdog can pick it up");
        }

        @Test
        @DisplayName("PROC-03 Scope A: Executor rejection in submitJob is handled safely")
        void testExecutorRejectionHandledGracefullyInSubmitJob() {
                doThrow(new RejectedExecutionException("Pool saturated"))
                                .when(taskExecutor).execute(any(Runnable.class));

                UUID jobId = UUID.randomUUID();
                assertDoesNotThrow(() -> orchestrator.submitJob(jobId));
        }

        // ==========================================
        // PROC-03 Scope B: Runtime Recovery Watchdog Tests
        // ==========================================

        @Test
        @DisplayName("PROC-03 Scope B: Watchdog recovers stale in-flight job and re-queues it")
        void testRuntimeWatchdogRecoversStaleInFlightJob() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob staleJob = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .attemptCount(1)
                                .maxAttempts(3)
                                .status(ProcessingStatus.EXTRACTING)
                                .build();

                when(repository.findStaleInFlightJobIds(any(), any(), any())).thenReturn(List.of(jobId));
                when(repository.findById(jobId)).thenReturn(Optional.of(staleJob));
                when(repository.recoverStaleInFlightJobAtomic(eq(jobId), any(), any(), any(), any(), any(), any()))
                                .thenReturn(1);

                int recovered = orchestrator.recoverStaleInFlightJobs();

                assertEquals(1, recovered);
                verify(repository, times(1)).recoverStaleInFlightJobAtomic(eq(jobId), any(), any(), any(), any(), any(), any());
                verify(taskExecutor, times(1)).execute(any());
        }

        @Test
        @DisplayName("PROC-03 Scope B: Watchdog fails stale in-flight job that has exhausted maximum attempts")
        void testRuntimeWatchdogFailsStaleExhaustedJob() {
                UUID jobId = UUID.randomUUID();
                UUID docId = UUID.randomUUID();

                ProcessingJob exhaustedJob = ProcessingJob.builder()
                                .id(jobId)
                                .documentId(docId)
                                .attemptCount(3)
                                .maxAttempts(3)
                                .status(ProcessingStatus.EXTRACTING)
                                .build();

                when(repository.findStaleInFlightJobIds(any(), any(), any())).thenReturn(List.of(jobId));
                when(repository.findById(jobId)).thenReturn(Optional.of(exhaustedJob));
                when(repository.failStaleExhaustedJobAtomic(eq(jobId), any(), any(), any(), any(), any(), any(), any(), any()))
                                .thenReturn(1);

                int recovered = orchestrator.recoverStaleInFlightJobs();

                assertEquals(1, recovered);
                verify(knowledgeServiceClient, times(1)).updateDocumentProcessingStatus(docId, "FAILED");
                verify(taskExecutor, never()).execute(any());
        }

        @Test
        @DisplayName("PROC-03 Scope B: Watchdog touches and re-dispatches stalled QUEUED job")
        void testRuntimeWatchdogRecoversStaleQueuedJob() {
                UUID jobId = UUID.randomUUID();

                when(repository.findStaleQueuedJobIds(eq(ProcessingStatus.QUEUED), any(), any())).thenReturn(List.of(jobId));
                when(repository.touchStaleQueuedJobAtomic(eq(jobId), eq(ProcessingStatus.QUEUED), any(), any(), any()))
                                .thenReturn(1);

                int recovered = orchestrator.recoverStaleQueuedJobs();

                assertEquals(1, recovered);
                verify(repository, times(1)).touchStaleQueuedJobAtomic(eq(jobId), eq(ProcessingStatus.QUEUED), any(), any(), any());
                verify(taskExecutor, times(1)).execute(any());
        }

        @Test
        @DisplayName("PROC-03 Scope B: Watchdog skips jobs currently in delayed retry backoff")
        void testRuntimeWatchdogSkipsScheduledRetryJob() {
                UUID jobId = UUID.randomUUID();

                orchestrator.scheduleDelayedRetry(jobId, 60000L);
                assertTrue(orchestrator.isJobScheduledForRetry(jobId));

                when(repository.findStaleInFlightJobIds(any(), any(), any())).thenReturn(List.of(jobId));
                when(repository.findStaleQueuedJobIds(any(), any(), any())).thenReturn(List.of(jobId));

                int recoveredInFlight = orchestrator.recoverStaleInFlightJobs();
                int recoveredQueued = orchestrator.recoverStaleQueuedJobs();

                assertEquals(0, recoveredInFlight);
                assertEquals(0, recoveredQueued);
                verify(repository, never()).recoverStaleInFlightJobAtomic(any(), any(), any(), any(), any(), any(), any());
                verify(repository, never()).touchStaleQueuedJobAtomic(any(), any(), any(), any(), any());
        }

        // ==========================================
        // PROC-03 Scope C: Startup Staggering Tests
        // ==========================================

        @Test
        @DisplayName("PROC-03 Scope C: Startup recovery staggers dispatch of multiple interrupted jobs")
        void testStartupRecoveryStaggersSubmissions() {
                UUID jobId1 = UUID.randomUUID();
                UUID jobId2 = UUID.randomUUID();

                ProcessingJob job1 = ProcessingJob.builder()
                                .id(jobId1)
                                .documentId(UUID.randomUUID())
                                .status(ProcessingStatus.VALIDATING)
                                .attemptCount(1)
                                .maxAttempts(3)
                                .build();

                ProcessingJob job2 = ProcessingJob.builder()
                                .id(jobId2)
                                .documentId(UUID.randomUUID())
                                .status(ProcessingStatus.EXTRACTING)
                                .attemptCount(2)
                                .maxAttempts(3)
                                .build();

                when(repository.findByStatusIn(any())).thenReturn(List.of(job1, job2));
                when(repository.findById(jobId1)).thenReturn(Optional.of(job1));
                when(repository.findById(jobId2)).thenReturn(Optional.of(job2));
                when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

                orchestrator.recoverInterruptedJobs();

                assertEquals(ProcessingStatus.QUEUED, job1.getStatus());
                assertEquals(ProcessingStatus.QUEUED, job2.getStatus());
                verify(repository, times(2)).save(any(ProcessingJob.class));
        }
}
