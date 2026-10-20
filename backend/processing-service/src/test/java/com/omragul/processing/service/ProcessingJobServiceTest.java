package com.omragul.processing.service;

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
import com.omragul.processing.service.impl.ProcessingJobServiceImpl;
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

    private ProcessingJobService service;

    @BeforeEach
    void setUp() {
        service = new ProcessingJobServiceImpl(repository, orchestrator, 3);
    }

    @Test
    @DisplayName("Should create and enqueue a new processing job")
    void testCreateJobSuccess() {
        UUID docId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .build();

        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());

        when(repository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
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
        assertEquals(ProcessingStatus.QUEUED, response.getStatus());

        verify(orchestrator, times(1)).submitJob(jobId);
    }

    @Test
    @DisplayName("Should return existing job for identical idempotency key")
    void testCreateJobIdempotency() {
        UUID docId = UUID.randomUUID();
        UUID existingJobId = UUID.randomUUID();
        String idemKey = "idem-key-123";

        ProcessingJob existing = ProcessingJob.builder()
                .id(existingJobId)
                .documentId(docId)
                .status(ProcessingStatus.VALIDATING)
                .idempotencyKey(idemKey)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findByIdempotencyKey(idemKey)).thenReturn(Optional.of(existing));

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .idempotencyKey(idemKey)
                .build();

        ProcessingJobResponse response = service.createJob(request);

        assertEquals(existingJobId, response.getId());
        assertEquals(ProcessingStatus.VALIDATING, response.getStatus());
        verify(repository, never()).save(any());
        verify(orchestrator, never()).submitJob(any());
    }

    @Test
    @DisplayName("Should reject job creation if document is already actively processing")
    void testCreateJobDuplicateRejection() {
        UUID docId = UUID.randomUUID();
        UUID activeJobId = UUID.randomUUID();

        ProcessingJob activeJob = ProcessingJob.builder()
                .id(activeJobId)
                .documentId(docId)
                .status(ProcessingStatus.EXTRACTING)
                .build();

        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.of(activeJob));

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
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
                .status(ProcessingStatus.COMPLETED)
                .build();

        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateTransitionException.class, () -> service.cancelJob(jobId));
    }
}
