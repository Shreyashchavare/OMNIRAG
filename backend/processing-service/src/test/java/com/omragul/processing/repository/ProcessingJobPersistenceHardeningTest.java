package com.omragul.processing.repository;

import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ProcessingJobPersistenceHardeningTest {

    @Autowired
    private ProcessingJobRepository repository;

    @Test
    @DisplayName("PROC-09 RED: Builder-created long current_step (>500 chars) must not overflow database column")
    void testBuilderCreatedLongCurrentStepTruncatedAtPersistence() {
        UUID docId = UUID.randomUUID();
        String step600 = "B".repeat(600);

        // Builder bypasses setCurrentStep setter
        ProcessingJob job = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .currentStep(step600)
                .build();

        // Without @PrePersist protection, this will fail with DataIntegrityViolationException on VARCHAR(500)
        ProcessingJob saved = repository.saveAndFlush(job);
        assertNotNull(saved.getId());
        assertTrue(saved.getCurrentStep().length() <= 500, "current_step must be <= 500 characters");
        assertEquals(500, saved.getCurrentStep().length());
        assertTrue(saved.getCurrentStep().endsWith("..."));
    }

    @Test
    @DisplayName("PROC-09: Setter-created long current_step (>500 chars) is defensively truncated")
    void testSetterCreatedLongCurrentStepTruncated() {
        UUID docId = UUID.randomUUID();
        String step600 = "S".repeat(600);

        ProcessingJob job = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .build();
        job.setCurrentStep(step600);

        ProcessingJob saved = repository.saveAndFlush(job);
        assertNotNull(saved.getId());
        assertEquals(500, saved.getCurrentStep().length());
        assertTrue(saved.getCurrentStep().endsWith("..."));
    }

    @Test
    @DisplayName("PROC-09: Entity update with long current_step (>500 chars) is truncated at @PreUpdate")
    void testEntityUpdateWithLongCurrentStepTruncated() {
        UUID docId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .currentStep("Initial valid step")
                .build();
        ProcessingJob saved = repository.saveAndFlush(job);

        // Update with 700 chars
        saved.setCurrentStep("U".repeat(700));
        ProcessingJob updated = repository.saveAndFlush(saved);

        assertEquals(500, updated.getCurrentStep().length());
        assertTrue(updated.getCurrentStep().endsWith("..."));
    }

    @Test
    @DisplayName("PROC-09: Exactly 500 characters is preserved without truncation or trailing ellipsis")
    void testExactly500CharactersPreserved() {
        UUID docId = UUID.randomUUID();
        String exact500 = "E".repeat(500);

        ProcessingJob job = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .currentStep(exact500)
                .build();

        ProcessingJob saved = repository.saveAndFlush(job);
        assertEquals(500, saved.getCurrentStep().length());
        assertEquals(exact500, saved.getCurrentStep());
        assertFalse(saved.getCurrentStep().endsWith("..."));
    }

    @Test
    @DisplayName("PROC-09: Null current_step is persisted safely")
    void testNullCurrentStepHandled() {
        UUID docId = UUID.randomUUID();

        ProcessingJob job = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .currentStep(null)
                .build();

        ProcessingJob saved = repository.saveAndFlush(job);
        assertNull(saved.getCurrentStep());
    }

    @Test
    @DisplayName("PROC-09: Full diagnostic details preserved in error_message without truncation")
    void testFullDiagnosticDetailsPreservedInErrorMessage() {
        UUID docId = UUID.randomUUID();
        String massiveDiagnosticTrace = "STACKTRACE_START\n" + "X".repeat(8000) + "\nSTACKTRACE_END";

        ProcessingJob job = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .errorCode("DOWNSTREAM_TIMEOUT")
                .errorMessage(massiveDiagnosticTrace)
                .currentStep("Job failed")
                .build();

        ProcessingJob saved = repository.saveAndFlush(job);
        assertNotNull(saved.getId());
        assertEquals(massiveDiagnosticTrace.length(), saved.getErrorMessage().length());
        assertEquals(massiveDiagnosticTrace, saved.getErrorMessage());
    }

    @Test
    @DisplayName("PROC-07: Database constraint must reject duplicate (document_id, idempotency_key)")
    void testDatabaseEnforcesIdempotencyUniqueness() {
        UUID docId = UUID.randomUUID();
        String idempotencyKey = "idem-test-key-" + UUID.randomUUID();

        ProcessingJob job1 = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .idempotencyKey(idempotencyKey)
                .build();
        repository.saveAndFlush(job1);

        ProcessingJob job2 = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .idempotencyKey(idempotencyKey)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(job2);
        }, "Database must enforce uniqueness on (document_id, idempotency_key)");
    }

    @Test
    @DisplayName("PROC-07: Multiple jobs with NULL idempotency_key are permitted for the same document")
    void testMultipleNullIdempotencyKeysPermitted() {
        UUID docId = UUID.randomUUID();

        ProcessingJob job1 = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .idempotencyKey(null)
                .build();
        repository.saveAndFlush(job1);

        ProcessingJob job2 = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .idempotencyKey(null)
                .build();

        assertDoesNotThrow(() -> {
            repository.saveAndFlush(job2);
        }, "Multiple rows with NULL idempotency_key must be allowed");
    }

    @Test
    @DisplayName("PROC-07: Same idempotency_key across different documents is permitted")
    void testSameIdempotencyKeyAcrossDifferentDocumentsPermitted() {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        String sharedKey = "shared-batch-key-123";

        ProcessingJob job1 = ProcessingJob.builder()
                .documentId(docId1)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .idempotencyKey(sharedKey)
                .build();
        repository.saveAndFlush(job1);

        ProcessingJob job2 = ProcessingJob.builder()
                .documentId(docId2)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .idempotencyKey(sharedKey)
                .build();

        assertDoesNotThrow(() -> {
            repository.saveAndFlush(job2);
        }, "Same idempotency_key with distinct document_ids must be allowed");
    }
}
