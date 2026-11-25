package com.omragul.processing.service;

import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.DocumentNotFoundException;
import com.omragul.processing.exception.DownstreamServiceException;
import com.omragul.processing.exception.InvalidDocumentStateException;
import com.omragul.processing.exception.KnowledgeBaseMismatchException;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PROC-06 Document and Knowledge Base Association Validation Tests.
 * Ensures that caller-provided knowledge_base_id is validated against authoritative metadata,
 * cross-KB contamination is prevented, and missing documents/associations are rejected cleanly.
 */
@ExtendWith(MockitoExtension.class)
class ProcessingJobDocumentValidationTest {

    @Mock
    private ProcessingJobRepository repository;

    @Mock
    private ProcessingOrchestrator orchestrator;

    @Mock
    private KnowledgeServiceClient knowledgeServiceClient;

    private ProcessingJobServiceImpl service;

    private final UUID testUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID authorizedKbId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID mismatchedKbId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        UserSecurityContextHolder.setContext(UserSecurityContext.user(testUserId, Set.of("ROLE_USER")));
        lenient().when(knowledgeServiceClient.verifyDocumentWriteAccess(any(), eq(testUserId))).thenReturn(true);
        service = new ProcessingJobServiceImpl(repository, orchestrator, knowledgeServiceClient, 3);
    }

    @AfterEach
    void tearDown() {
        UserSecurityContextHolder.clear();
    }

    @Test
    @DisplayName("PROC-06: Matching document and knowledge base are accepted")
    void testMatchingKnowledgeBaseAccepted() {
        UUID docId = UUID.randomUUID();
        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(authorizedKbId)
                .ownerId(testUserId)
                .build()));

        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ProcessingJob.class))).thenAnswer(inv -> {
            ProcessingJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(authorizedKbId)
                .build();

        ProcessingJobResponse response = service.createJob(request);

        assertNotNull(response);
        assertEquals(authorizedKbId, response.getKnowledgeBaseId());
        verify(orchestrator, times(1)).submitJob(any());
    }

    @Test
    @DisplayName("PROC-06: When knowledge_base_id is omitted by caller, automatically binds to document's authoritative KB")
    void testOmittedKnowledgeBaseBindsToAuthoritativeDocKb() {
        UUID docId = UUID.randomUUID();
        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(authorizedKbId)
                .ownerId(testUserId)
                .build()));

        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ProcessingJob.class))).thenAnswer(inv -> {
            ProcessingJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        // Caller omits knowledgeBaseId
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(null)
                .build();

        ProcessingJobResponse response = service.createJob(request);

        assertNotNull(response);
        assertEquals(authorizedKbId, response.getKnowledgeBaseId(),
                "Job must be bound to the document's authoritative knowledge base ID");
    }

    @Test
    @DisplayName("PROC-06: Document belonging to another knowledge base is rejected with KnowledgeBaseMismatchException (400)")
    void testMismatchedKnowledgeBaseRejected() {
        UUID docId = UUID.randomUUID();
        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(authorizedKbId) // Actual document KB is authorizedKbId
                .ownerId(testUserId)
                .build()));

        // Caller requests processing under mismatchedKbId
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(mismatchedKbId)
                .build();

        KnowledgeBaseMismatchException ex = assertThrows(KnowledgeBaseMismatchException.class,
                () -> service.createJob(request));

        assertTrue(ex.getMessage().contains(mismatchedKbId.toString()));
        assertTrue(ex.getMessage().contains(authorizedKbId.toString()));

        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
        verify(orchestrator, never()).submitJob(any());
    }

    @Test
    @DisplayName("PROC-06: Nonexistent document in Knowledge Service is rejected with DocumentNotFoundException (404)")
    void testNonexistentDocumentRejected() {
        UUID docId = UUID.randomUUID();
        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.empty());

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(authorizedKbId)
                .build();

        DocumentNotFoundException ex = assertThrows(DocumentNotFoundException.class,
                () -> service.createJob(request));

        assertTrue(ex.getMessage().contains(docId.toString()));
        verify(repository, never()).save(any());
        verify(orchestrator, never()).submitJob(any());
    }

    @Test
    @DisplayName("PROC-06: Document without knowledge base association is rejected with InvalidDocumentStateException (400)")
    void testDocumentWithoutKnowledgeBaseRejected() {
        UUID docId = UUID.randomUUID();
        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(null) // Unassociated document
                .ownerId(testUserId)
                .build()));

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .build();

        InvalidDocumentStateException ex = assertThrows(InvalidDocumentStateException.class,
                () -> service.createJob(request));

        assertTrue(ex.getMessage().contains("Document is not associated with any knowledge base"));
        verify(repository, never()).save(any());
        verify(orchestrator, never()).submitJob(any());
    }

    @Test
    @DisplayName("PROC-06: Downstream Knowledge Service network failure does not create an invalid job")
    void testKnowledgeServiceFailureHandledSafely() {
        UUID docId = UUID.randomUUID();
        when(knowledgeServiceClient.getDocument(docId))
                .thenThrow(new DownstreamServiceException("knowledge-service", 503, "Service Unavailable"));

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(authorizedKbId)
                .build();

        assertThrows(DownstreamServiceException.class, () -> service.createJob(request));
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
        verify(orchestrator, never()).submitJob(any());
    }

    @Test
    @DisplayName("VULN-3.3-02: Collaborator with WRITE permission can create processing job")
    void testCollaboratorWithWriteAccessCanCreateJob() {
        UUID docId = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(authorizedKbId)
                .ownerId(otherOwner)
                .build()));
        when(knowledgeServiceClient.verifyDocumentWriteAccess(docId, testUserId)).thenReturn(true);
        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ProcessingJob.class))).thenAnswer(inv -> {
            ProcessingJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(authorizedKbId)
                .build();

        ProcessingJobResponse response = service.createJob(request);
        assertNotNull(response);
        assertEquals(testUserId, response.getOwnerId(), "Collaborator who submitted job becomes the job owner");
    }

    @Test
    @DisplayName("VULN-3.3-02: Collaborator with READ-only permission is rejected and document existence is concealed (404)")
    void testCollaboratorWithReadOnlyAccessRejectedWith404() {
        UUID docId = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(authorizedKbId)
                .ownerId(otherOwner)
                .build()));
        when(knowledgeServiceClient.verifyDocumentWriteAccess(docId, testUserId)).thenReturn(false);

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(authorizedKbId)
                .build();

        assertThrows(DocumentNotFoundException.class, () -> service.createJob(request));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("VULN-3.3-02: Ownerless historical document can be processed if caller has verified write access")
    void testOwnerlessDocumentWithWriteAccessCanCreateJob() {
        UUID docId = UUID.randomUUID();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(authorizedKbId)
                .ownerId(null) // ownerless legacy document
                .build()));
        when(knowledgeServiceClient.verifyDocumentWriteAccess(docId, testUserId)).thenReturn(true);
        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ProcessingJob.class))).thenAnswer(inv -> {
            ProcessingJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(authorizedKbId)
                .build();

        ProcessingJobResponse response = service.createJob(request);
        assertNotNull(response);
        assertEquals(testUserId, response.getOwnerId(), "Newly created job for ownerless document gets authenticated caller as owner");
    }

    @Test
    @DisplayName("VULN-3.3-02: Ownerless historical document without write access is rejected (404)")
    void testOwnerlessDocumentWithoutWriteAccessRejectedWith404() {
        UUID docId = UUID.randomUUID();

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(authorizedKbId)
                .ownerId(null)
                .build()));
        when(knowledgeServiceClient.verifyDocumentWriteAccess(docId, testUserId)).thenReturn(false);

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(authorizedKbId)
                .build();

        assertThrows(DocumentNotFoundException.class, () -> service.createJob(request));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("VULN-3.3-02: Internal service can submit processing job without caller user id")
    void testInternalServiceBypassesCollaboratorCheck() {
        UUID docId = UUID.randomUUID();
        UUID docOwner = UUID.randomUUID();

        UserSecurityContextHolder.setContext(UserSecurityContext.internal());

        when(knowledgeServiceClient.getDocument(docId)).thenReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(authorizedKbId)
                .ownerId(docOwner)
                .build()));
        when(repository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(eq(docId), any()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ProcessingJob.class))).thenAnswer(inv -> {
            ProcessingJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .knowledgeBaseId(authorizedKbId)
                .build();

        ProcessingJobResponse response = service.createJob(request);
        assertNotNull(response);
        assertEquals(docOwner, response.getOwnerId(), "Internal service inherits document ownerId for job");
        verify(knowledgeServiceClient, never()).verifyDocumentWriteAccess(any(), any());
    }
}
