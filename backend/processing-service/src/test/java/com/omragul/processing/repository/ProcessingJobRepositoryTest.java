package com.omragul.processing.repository;

import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ProcessingJobRepositoryTest {

    @Autowired
    private ProcessingJobRepository repository;

    @Test
    @DisplayName("Should persist and retrieve a processing job")
    void testPersistAndRetrieveJob() {
        UUID documentId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .documentId(documentId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .attemptCount(0)
                .maxAttempts(3)
                .idempotencyKey("idem-" + UUID.randomUUID())
                .build();

        ProcessingJob saved = repository.save(job);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(ProcessingStatus.QUEUED, saved.getStatus());

        Optional<ProcessingJob> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(documentId, found.get().getDocumentId());
    }

    @Test
    @DisplayName("Should detect active jobs for document")
    void testExistsByDocumentIdAndStatusIn() {
        UUID documentId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .documentId(documentId)
                .status(ProcessingStatus.EXTRACTING)
                .stage(ProcessingStage.EXTRACTION)
                .build();
        repository.save(job);

        boolean activeExists = repository.existsByDocumentIdAndStatusIn(
                documentId,
                List.of(ProcessingStatus.QUEUED, ProcessingStatus.VALIDATING, ProcessingStatus.EXTRACTING)
        );
        assertTrue(activeExists);

        boolean completedExists = repository.existsByDocumentIdAndStatusIn(
                documentId,
                List.of(ProcessingStatus.COMPLETED)
        );
        assertFalse(completedExists);
    }

    @Test
    @DisplayName("Should find jobs by status with pagination")
    void testFindByStatusPageable() {
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();

        repository.save(ProcessingJob.builder().documentId(doc1).status(ProcessingStatus.QUEUED).build());
        repository.save(ProcessingJob.builder().documentId(doc2).status(ProcessingStatus.QUEUED).build());

        Page<ProcessingJob> page = repository.findByStatus(ProcessingStatus.QUEUED, PageRequest.of(0, 10));
        assertTrue(page.getTotalElements() >= 2);
    }

    @Test
    @DisplayName("Should find interrupted active jobs for recovery")
    void testFindInterruptedJobsForRecovery() {
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();

        repository.save(ProcessingJob.builder().documentId(doc1).status(ProcessingStatus.VALIDATING).stage(ProcessingStage.VALIDATION).build());
        repository.save(ProcessingJob.builder().documentId(doc2).status(ProcessingStatus.COMPLETED).stage(ProcessingStage.COMPLETED).build());

        List<ProcessingJob> interrupted = repository.findByStatusIn(List.of(
                ProcessingStatus.QUEUED, ProcessingStatus.VALIDATING, ProcessingStatus.EXTRACTING,
                ProcessingStatus.CHUNKING, ProcessingStatus.EMBEDDING_INDEXING
        ));

        assertFalse(interrupted.isEmpty());
        assertTrue(interrupted.stream().anyMatch(j -> j.getDocumentId().equals(doc1)));
        assertFalse(interrupted.stream().anyMatch(j -> j.getDocumentId().equals(doc2)));
    }

    @Test
    @DisplayName("PROC-13: claimJobAtomic atomically transitions QUEUED job and increments version and attempt count")
    void testClaimJobAtomicSuccess() {
        ProcessingJob job = ProcessingJob.builder()
                .documentId(UUID.randomUUID())
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .attemptCount(0)
                .maxAttempts(3)
                .build();
        ProcessingJob saved = repository.saveAndFlush(job);
        UUID jobId = saved.getId();
        Long initialVersion = saved.getVersion();
        java.time.Instant now = java.time.Instant.now();

        int rowsUpdated = repository.claimJobAtomic(
                jobId,
                ProcessingStatus.QUEUED,
                ProcessingStatus.VALIDATING,
                ProcessingStage.VALIDATION,
                "Validating document eligibility",
                now
        );

        assertEquals(1, rowsUpdated);

        Optional<ProcessingJob> reloaded = repository.findById(jobId);
        assertTrue(reloaded.isPresent());
        ProcessingJob claimed = reloaded.get();
        assertEquals(ProcessingStatus.VALIDATING, claimed.getStatus());
        assertEquals(ProcessingStage.VALIDATION, claimed.getStage());
        assertEquals(1, claimed.getAttemptCount());
        assertEquals("Validating document eligibility", claimed.getCurrentStep());
        assertNotNull(claimed.getStartedAt());
        assertNotNull(claimed.getUpdatedAt());
        assertEquals(initialVersion + 1, claimed.getVersion());
    }

    @Test
    @DisplayName("PROC-13: claimJobAtomic returns 0 when job is not in expected QUEUED status")
    void testClaimJobAtomicNonQueuedFails() {
        ProcessingJob job = ProcessingJob.builder()
                .documentId(UUID.randomUUID())
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .build();
        ProcessingJob saved = repository.saveAndFlush(job);
        UUID jobId = saved.getId();
        Long initialVersion = saved.getVersion();

        int rowsUpdated = repository.claimJobAtomic(
                jobId,
                ProcessingStatus.QUEUED,
                ProcessingStatus.VALIDATING,
                ProcessingStage.VALIDATION,
                "Validating document eligibility",
                java.time.Instant.now()
        );

        assertEquals(0, rowsUpdated);

        Optional<ProcessingJob> reloaded = repository.findById(jobId);
        assertTrue(reloaded.isPresent());
        assertEquals(ProcessingStatus.FAILED, reloaded.get().getStatus());
        assertEquals(initialVersion, reloaded.get().getVersion());
        assertEquals(1, reloaded.get().getAttemptCount());
    }

    @Test
    @DisplayName("PROC-13: claimJobAtomic returns 0 for non-existent job ID")
    void testClaimJobAtomicNonExistentJobReturnsZero() {
        int rowsUpdated = repository.claimJobAtomic(
                UUID.randomUUID(),
                ProcessingStatus.QUEUED,
                ProcessingStatus.VALIDATING,
                ProcessingStage.VALIDATION,
                "Validating document eligibility",
                java.time.Instant.now()
        );
        assertEquals(0, rowsUpdated);
    }
}
