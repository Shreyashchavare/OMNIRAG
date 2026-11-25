package com.omragul.processing.security;

import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.JobNotFoundException;
import com.omragul.processing.repository.ProcessingJobRepository;
import com.omragul.processing.service.ProcessingOrchestrator;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PROC-01 Comprehensive Security & Ownership Verification Tests.
 * Tests IDOR prevention, user isolation, admin overrides, legacy ownerless jobs,
 * and header/identity enforcement.
 */
@ExtendWith(MockitoExtension.class)
class ProcessingJobSecurityTest {

    @Mock
    private ProcessingJobRepository repository;

    @Mock
    private ProcessingOrchestrator orchestrator;

    @Mock
    private KnowledgeServiceClient knowledgeServiceClient;

    private ProcessingJobServiceImpl service;

    private final UUID userA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID adminUser = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private final UUID commonKbId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        service = new ProcessingJobServiceImpl(repository, orchestrator, knowledgeServiceClient, 3);
    }

    @AfterEach
    void tearDown() {
        UserSecurityContextHolder.clear();
    }

    @Test
    @DisplayName("PROC-01: User A creates a job - job ownerId is strictly set to User A")
    void testCreateJobSetsAuthenticatedOwner() {
        UserSecurityContextHolder.setContext(UserSecurityContext.user(userA, Set.of("ROLE_USER")));

        UUID docId = UUID.randomUUID();
        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(commonKbId)
                .ownerId(userA)
                .build()));
        when(knowledgeServiceClient.verifyDocumentWriteAccess(eq(docId), eq(userA))).thenReturn(true);

        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());

        when(repository.saveAndFlush(any(ProcessingJob.class))).thenAnswer(inv -> {
            ProcessingJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(commonKbId)
                .build();

        ProcessingJobResponse response = service.createJob(request);

        assertNotNull(response);
        assertEquals(userA, response.getOwnerId(), "Newly created job must be owned by authenticated User A");
    }

    @Test
    @DisplayName("PROC-01: User A cannot access User B's job (returns 404 JobNotFoundException to prevent existence leak)")
    void testUserCannotAccessOtherUserJob() {
        UUID jobBId = UUID.randomUUID();
        ProcessingJob jobB = ProcessingJob.builder()
                .id(jobBId)
                .documentId(UUID.randomUUID())
                .knowledgeBaseId(commonKbId)
                .ownerId(userB)
                .status(ProcessingStatus.VALIDATING)
                .build();

        when(repository.findById(jobBId)).thenReturn(Optional.of(jobB));

        // Caller is User A
        UserSecurityContextHolder.setContext(UserSecurityContext.user(userA, Set.of("ROLE_USER")));

        assertThrows(JobNotFoundException.class, () -> service.getJobById(jobBId),
                "User A must receive JobNotFoundException when querying User B's job");
        assertThrows(JobNotFoundException.class, () -> service.getJobStatus(jobBId),
                "User A must receive JobNotFoundException when querying User B's job status");
    }

    @Test
    @DisplayName("PROC-01: User B cannot retry User A's failed job")
    void testUserCannotRetryOtherUserJob() {
        UUID jobAId = UUID.randomUUID();
        ProcessingJob jobA = ProcessingJob.builder()
                .id(jobAId)
                .documentId(UUID.randomUUID())
                .knowledgeBaseId(commonKbId)
                .ownerId(userA)
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .build();

        when(repository.findById(jobAId)).thenReturn(Optional.of(jobA));

        // Caller is User B
        UserSecurityContextHolder.setContext(UserSecurityContext.user(userB, Set.of("ROLE_USER")));

        assertThrows(JobNotFoundException.class, () -> service.retryJob(jobAId),
                "User B must not be permitted to retry User A's job");
        verify(orchestrator, never()).submitJob(any());
    }

    @Test
    @DisplayName("PROC-01: User B cannot cancel User A's active job")
    void testUserCannotCancelOtherUserJob() {
        UUID jobAId = UUID.randomUUID();
        ProcessingJob jobA = ProcessingJob.builder()
                .id(jobAId)
                .documentId(UUID.randomUUID())
                .knowledgeBaseId(commonKbId)
                .ownerId(userA)
                .status(ProcessingStatus.VALIDATING)
                .stage(ProcessingStage.VALIDATION)
                .build();

        when(repository.findById(jobAId)).thenReturn(Optional.of(jobA));

        // Caller is User B
        UserSecurityContextHolder.setContext(UserSecurityContext.user(userB, Set.of("ROLE_USER")));

        assertThrows(JobNotFoundException.class, () -> service.cancelJob(jobAId),
                "User B must not be permitted to cancel User A's job");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("PROC-01: User listing is strictly scoped to the authenticated user's jobs")
    void testListJobsScopedToAuthenticatedOwner() {
        UserSecurityContextHolder.setContext(UserSecurityContext.user(userA, Set.of("ROLE_USER")));

        ProcessingJob jobA = ProcessingJob.builder()
                .id(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .ownerId(userA)
                .status(ProcessingStatus.COMPLETED)
                .build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(repository.findByOwnerId(eq(userA), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(jobA), pageable, 1));

        Page<ProcessingJobResponse> result = service.getJobs(null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(userA, result.getContent().get(0).getOwnerId());
        verify(repository, times(1)).findByOwnerId(userA, pageable);
        verify(repository, never()).findAll(any(PageRequest.class));
    }

    @Test
    @DisplayName("PROC-01: Admin user can list all jobs across users")
    void testAdminCanListAllJobs() {
        UserSecurityContextHolder.setContext(UserSecurityContext.user(adminUser, Set.of("ROLE_ADMIN")));

        ProcessingJob jobA = ProcessingJob.builder().id(UUID.randomUUID()).ownerId(userA).status(ProcessingStatus.COMPLETED).build();
        ProcessingJob jobB = ProcessingJob.builder().id(UUID.randomUUID()).ownerId(userB).status(ProcessingStatus.QUEUED).build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(repository.findAll(eq(pageable))).thenReturn(new PageImpl<>(List.of(jobA, jobB), pageable, 2));

        Page<ProcessingJobResponse> result = service.getJobs(null, pageable);

        assertEquals(2, result.getTotalElements());
        verify(repository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("PROC-01: Admin user can access and manage any user's job")
    void testAdminCanAccessAnyUserJob() {
        UserSecurityContextHolder.setContext(UserSecurityContext.user(adminUser, Set.of("ROLE_ADMIN")));

        UUID jobAId = UUID.randomUUID();
        ProcessingJob jobA = ProcessingJob.builder()
                .id(jobAId)
                .documentId(UUID.randomUUID())
                .ownerId(userA)
                .status(ProcessingStatus.VALIDATING)
                .stage(ProcessingStage.VALIDATION)
                .build();

        when(repository.findById(jobAId)).thenReturn(Optional.of(jobA));

        ProcessingJobResponse response = service.getJobById(jobAId);
        assertNotNull(response);
        assertEquals(jobAId, response.getId());
    }

    @Test
    @DisplayName("PROC-01: Legacy ownerless job (ownerId = NULL) is inaccessible to ordinary users")
    void testLegacyOwnerlessJobInaccessibleToOrdinaryUser() {
        UUID legacyJobId = UUID.randomUUID();
        ProcessingJob legacyJob = ProcessingJob.builder()
                .id(legacyJobId)
                .documentId(UUID.randomUUID())
                .ownerId(null) // historical record prior to V3
                .status(ProcessingStatus.COMPLETED)
                .build();

        when(repository.findById(legacyJobId)).thenReturn(Optional.of(legacyJob));

        // Ordinary user attempts access
        UserSecurityContextHolder.setContext(UserSecurityContext.user(userA, Set.of("ROLE_USER")));

        assertThrows(JobNotFoundException.class, () -> service.getJobById(legacyJobId),
                "Ordinary user must receive 404 for legacy ownerless job");
    }

    @Test
    @DisplayName("PROC-01: Legacy ownerless job is accessible to internal service")
    void testLegacyOwnerlessJobAccessibleToInternalService() {
        UUID legacyJobId = UUID.randomUUID();
        ProcessingJob legacyJob = ProcessingJob.builder()
                .id(legacyJobId)
                .documentId(UUID.randomUUID())
                .ownerId(null)
                .status(ProcessingStatus.COMPLETED)
                .build();

        when(repository.findById(legacyJobId)).thenReturn(Optional.of(legacyJob));

        // Internal service call
        UserSecurityContextHolder.setContext(UserSecurityContext.internal());

        ProcessingJobResponse response = service.getJobById(legacyJobId);
        assertNotNull(response);
        assertNull(response.getOwnerId(), "Legacy job ownerId remains null (no fabricated owners)");
    }

    @Test
    @DisplayName("PROC-01 & VULN-3.3-03: Internal service cannot perform unscoped job listings")
    void testInternalServiceCannotPerformUnscopedListing() {
        UserSecurityContextHolder.setContext(UserSecurityContext.internal());

        assertThrows(com.omragul.processing.exception.AccessDeniedException.class, () ->
                service.getJobs(null, PageRequest.of(0, 10)),
                "Internal service must be denied unscoped job listings");
    }

    @Test
    @DisplayName("PROC-01 & VULN-3.3-03: Internal service cannot arbitrarily cancel user jobs")
    void testInternalServiceCannotCancelUserJob() {
        UUID jobId = UUID.randomUUID();
        UserSecurityContextHolder.setContext(UserSecurityContext.internal());

        assertThrows(com.omragul.processing.exception.AccessDeniedException.class, () ->
                service.cancelJob(jobId),
                "Internal service must be denied arbitrary job cancellations");
    }

    @Test
    @DisplayName("PROC-01 & VULN-3.3-03: Internal service cannot arbitrarily retry user jobs")
    void testInternalServiceCannotRetryUserJob() {
        UUID jobId = UUID.randomUUID();
        UserSecurityContextHolder.setContext(UserSecurityContext.internal());

        assertThrows(com.omragul.processing.exception.AccessDeniedException.class, () ->
                service.retryJob(jobId),
                "Internal service must be denied arbitrary job retries");
    }
}
