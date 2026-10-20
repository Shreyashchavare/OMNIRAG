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
}
