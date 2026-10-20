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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                taskExecutor
        );
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
        when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentDto docDto = DocumentDto.builder()
                .id(docId)
                .name("test-doc.pdf")
                .mimeType("application/pdf")
                .status("ACTIVE")
                .build();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(docDto));
        when(multimodalServiceClient.extractText(docDto)).thenReturn("Sample extracted document text");
        when(multimodalServiceClient.createChunks("Sample extracted document text")).thenReturn(List.of("Chunk 1", "Chunk 2"));
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
        when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

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
        when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentDto docDto = DocumentDto.builder()
                .id(docId)
                .name("test.txt")
                .mimeType("text/plain")
                .status("ACTIVE")
                .build();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(docDto));
        when(multimodalServiceClient.extractText(docDto))
                .thenThrow(new DownstreamServiceException("multimodal-service", 503, "Service unavailable"));

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
        when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

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
        when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

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
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .documentId(UUID.randomUUID())
                .status(ProcessingStatus.QUEUED)
                .attemptCount(0)
                .maxAttempts(3)
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));
        when(repository.save(any(ProcessingJob.class))).thenThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(ProcessingJob.class, jobId)
        );

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
        when(repository.save(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

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
}
