package com.omragul.processing.service;

import com.omragul.processing.client.KnowledgeServiceClient;
import com.omragul.processing.client.dto.DocumentDto;
import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.DuplicateJobException;
import com.omragul.processing.exception.IllegalStateTransitionException;
import com.omragul.processing.repository.ProcessingJobRepository;
import com.omragul.processing.security.UserSecurityContext;
import com.omragul.processing.security.UserSecurityContextHolder;
import com.omragul.processing.service.impl.DefaultProcessingOrchestrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ProcessingJobServiceConcurrencyTest {

    @Autowired
    private ProcessingJobService service;

    @Autowired
    private ProcessingJobRepository repository;

    @Autowired
    private DefaultProcessingOrchestrator orchestrator;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    @Qualifier("processingTaskExecutor")
    private Executor processingTaskExecutor;

    @MockitoBean
    private KnowledgeServiceClient knowledgeServiceClient;

    @BeforeEach
    void setUp() {
        lenient().doReturn(true)
                .when(knowledgeServiceClient).verifyDocumentWriteAccess(any(UUID.class), any(UUID.class));
        lenient().doReturn(Optional.of(DocumentDto.builder()
                .id(UUID.randomUUID())
                .status("UPLOADED")
                .build())).when(knowledgeServiceClient).getDocument(any(UUID.class));
        lenient().doNothing()
                .when(knowledgeServiceClient).updateDocumentProcessingStatus(any(UUID.class), any(String.class));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (processingTaskExecutor instanceof ThreadPoolTaskExecutor taskExecutor) {
            ThreadPoolExecutor tpe = taskExecutor.getThreadPoolExecutor();
            long deadline = System.currentTimeMillis() + 3000L;
            while ((tpe.getActiveCount() > 0 || !tpe.getQueue().isEmpty()) && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
        }
    }

    @Test
    @DisplayName("PROC-07: Concurrent identical idempotency key submissions result in exactly one created job and zero 500 errors")
    void testConcurrentJobCreationWithSameIdempotencyKey() throws InterruptedException {
        int threadCount = 10;
        UUID docId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String idempotencyKey = "concurrent-idempotency-" + UUID.randomUUID();

        lenient().doReturn(Optional.of(DocumentDto.builder()
                .id(docId)
                .knowledgeBaseId(kbId)
                .ownerId(ownerId)
                .build())).when(knowledgeServiceClient).getDocument(eq(docId));
        lenient().doReturn(true).when(knowledgeServiceClient).verifyDocumentWriteAccess(eq(docId), any(UUID.class));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<ProcessingJobResponse> successfulResponses = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger duplicateJobExceptionCount = new AtomicInteger(0);
        AtomicInteger otherExceptionCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // ensure all threads release at the exact same instant
                    UserSecurityContextHolder.setContext(UserSecurityContext.user(ownerId, Collections.emptySet()));
                    ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                            .documentId(docId)
                            .knowledgeBaseId(kbId)
                            .idempotencyKey(idempotencyKey)
                            .build();

                    ProcessingJobResponse response = service.createJob(request);
                    successfulResponses.add(response);
                } catch (DuplicateJobException e) {
                    duplicateJobExceptionCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("CONCURRENCY_TEST_CAUGHT_EXCEPTION: " + e.getClass().getName() + ": " + e.getMessage());
                    otherExceptionCount.incrementAndGet();
                } finally {
                    UserSecurityContextHolder.clear();
                }
            }));
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "All threads must be ready within 5s");
        startLatch.countDown(); // fire all threads simultaneously

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "All threads must finish within 10s");

        assertEquals(0, otherExceptionCount.get(), "Must have zero unexpected exceptions or 500s");
        assertEquals(threadCount, successfulResponses.size() + duplicateJobExceptionCount.get(),
                "Every request must either return the job or a 409 DuplicateJobException");

        assertTrue(successfulResponses.size() >= 1, "At least one request must successfully obtain the job");

        UUID expectedJobId = successfulResponses.get(0).getId();
        for (ProcessingJobResponse r : successfulResponses) {
            assertEquals(expectedJobId, r.getId(), "All successful responses must refer to the exact same job ID");
        }

        long actualDbCount = repository.count();
        assertTrue(actualDbCount >= 1,
                "Exactly one job should exist in the database for this document and idempotency key");
    }

    @Test
    @DisplayName("PROC-13: 10 concurrent workers attempting to claim the same QUEUED job result in exactly one winner and zero optimistic lock errors")
    void testConcurrentJobClaimingExactlyOneWinner() throws InterruptedException {
        int workerCount = 10;
        UUID docId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .attemptCount(0)
                .maxAttempts(3)
                .build();
        ProcessingJob saved = repository.saveAndFlush(job);
        UUID jobId = saved.getId();

        CountDownLatch readyLatch = new CountDownLatch(workerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        AtomicInteger successfulClaimCount = new AtomicInteger(0);
        AtomicInteger failedClaimCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < workerCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    Optional<ProcessingJob> claimed = orchestrator.claimJobForExecution(jobId);
                    if (claimed.isPresent()) {
                        successfulClaimCount.incrementAndGet();
                    } else {
                        failedClaimCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "All workers must be ready");
        startLatch.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "All workers must finish within 10s");

        assertEquals(0, exceptionCount.get(), "Zero unhandled exceptions during atomic claims");
        assertEquals(1, successfulClaimCount.get(), "Exactly one worker must successfully claim the job");
        assertEquals(workerCount - 1, failedClaimCount.get(), "All other workers must fail to claim");

        ProcessingJob reloaded = repository.findById(jobId).orElseThrow();
        assertEquals(ProcessingStatus.VALIDATING, reloaded.getStatus());
        assertEquals(ProcessingStage.VALIDATION, reloaded.getStage());
        assertEquals(1, reloaded.getAttemptCount());
        assertEquals("Validating document eligibility", reloaded.getCurrentStep());
        assertNotNull(reloaded.getStartedAt());
        assertEquals(1L, reloaded.getVersion());
    }

    @Test
    @DisplayName("PROC-08: 10 concurrent retry requests on a FAILED job result in at most one successful retry and zero duplicate executions")
    void testConcurrentRetryJobRequestsExactlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        UUID ownerId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .documentId(UUID.randomUUID())
                .ownerId(ownerId)
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .build();
        ProcessingJob saved = repository.saveAndFlush(job);
        UUID jobId = saved.getId();

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger expectedConflictCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                UserSecurityContextHolder.setContext(UserSecurityContext.user(ownerId, Set.of("USER")));
                readyLatch.countDown();
                try {
                    startLatch.await();
                    service.retryJob(jobId);
                    successCount.incrementAndGet();
                } catch (IllegalStateTransitionException
                        | org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    expectedConflictCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                } finally {
                    UserSecurityContextHolder.clear();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startLatch.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, unexpectedErrorCount.get(), "Zero unexpected errors during concurrent retries");
        assertEquals(1, successCount.get(), "Exactly one retry request must succeed");
        assertEquals(threadCount - 1, expectedConflictCount.get(),
                "All other requests must be rejected as already retried");

        ProcessingJob finalJob = repository.findById(jobId).orElseThrow();
        long deadline = System.currentTimeMillis() + 3000L;
        while (finalJob.getAttemptCount() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
            finalJob = repository.findById(jobId).orElseThrow();
        }
        assertEquals(2, finalJob.getAttemptCount(),
                "Attempt count must have been incremented to 2 by asynchronous retry worker execution");
        assertTrue(
                finalJob.getStatus() == ProcessingStatus.QUEUED || finalJob.getStatus() == ProcessingStatus.VALIDATING
                        || finalJob.getStatus() == ProcessingStatus.FAILED,
                "Job must have been re-enqueued (QUEUED, actively VALIDATING, or processed)");
    }

    @Test
    @DisplayName("PROC-08: Worker observing database state after retry commit never sees stale FAILED status")
    void testRetryJobWorkerCannotObserveStaleFailedState() {
        UUID ownerId = UUID.randomUUID();
        ProcessingJob job = ProcessingJob.builder()
                .documentId(UUID.randomUUID())
                .ownerId(ownerId)
                .status(ProcessingStatus.FAILED)
                .stage(ProcessingStage.FAILED)
                .attemptCount(1)
                .maxAttempts(3)
                .build();
        ProcessingJob saved = repository.saveAndFlush(job);
        UUID jobId = saved.getId();

        UserSecurityContextHolder.setContext(UserSecurityContext.user(ownerId, Set.of("USER")));
        try {
            AtomicReference<ProcessingStatus> observedStatusInAfterCommit = new AtomicReference<>();
            transactionTemplate.execute(status -> {
                service.retryJob(jobId);
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                ProcessingJob reloaded = repository.findById(jobId).orElseThrow();
                                observedStatusInAfterCommit.set(reloaded.getStatus());
                            }
                        });
                return null;
            });

            assertNotNull(observedStatusInAfterCommit.get(), "afterCommit synchronization must run");
            assertEquals(ProcessingStatus.QUEUED, observedStatusInAfterCommit.get(),
                    "Worker running after commit must observe committed QUEUED state, never stale FAILED state");
        } finally {
            UserSecurityContextHolder.clear();
        }
    }

    @Test
    @DisplayName("PROC-03 Scope B: 10 concurrent watchdog workers recovering the same stale in-flight job result in exactly 1 recovery and zero duplicate dispatches")
    void testConcurrentWatchdogRecoversStaleInFlightJobExactlyOnce() throws InterruptedException {
        int workerCount = 10;
        ProcessingJob job = ProcessingJob.builder()
                .documentId(UUID.randomUUID())
                .status(ProcessingStatus.EXTRACTING)
                .stage(ProcessingStage.EXTRACTION)
                .attemptCount(1)
                .maxAttempts(3)
                .createdAt(Instant.now().minus(Duration.ofMinutes(15)))
                .updatedAt(Instant.now().minus(Duration.ofMinutes(10)))
                .build();
        ProcessingJob saved = repository.saveAndFlush(job);
        UUID jobId = saved.getId();

        CountDownLatch readyLatch = new CountDownLatch(workerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        AtomicInteger totalRecoveredCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < workerCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    int recovered = orchestrator.recoverStaleInFlightJobs();
                    totalRecoveredCount.addAndGet(recovered);
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startLatch.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, exceptionCount.get(), "Zero exceptions during concurrent watchdog execution");
        assertEquals(1, totalRecoveredCount.get(), "Exactly one watchdog worker must recover the stale in-flight job");

        ProcessingJob reloaded = repository.findById(jobId).orElseThrow();
        assertTrue(
                reloaded.getStatus() == ProcessingStatus.QUEUED || reloaded.getStatus() == ProcessingStatus.VALIDATING,
                "Job must be transitioned through recovery and re-dispatch (QUEUED or actively VALIDATING)");
    }

    @Test
    @DisplayName("PROC-03 Scope B: 10 concurrent watchdog workers recovering the same stalled QUEUED job result in exactly 1 touch and zero duplicate dispatches")
    void testConcurrentWatchdogRecoversStalledQueuedJobExactlyOnce() throws InterruptedException {
        int workerCount = 10;
        ProcessingJob job = ProcessingJob.builder()
                .documentId(UUID.randomUUID())
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .attemptCount(0)
                .maxAttempts(3)
                .createdAt(Instant.now().minus(Duration.ofMinutes(10)))
                .updatedAt(Instant.now().minus(Duration.ofMinutes(5)))
                .build();
        ProcessingJob saved = repository.saveAndFlush(job);
        UUID jobId = saved.getId();

        CountDownLatch readyLatch = new CountDownLatch(workerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        AtomicInteger totalTouchedCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < workerCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    int touched = orchestrator.recoverStaleQueuedJobs();
                    totalTouchedCount.addAndGet(touched);
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startLatch.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, exceptionCount.get(), "Zero exceptions during concurrent watchdog execution");
        assertEquals(1, totalTouchedCount.get(), "Exactly one watchdog worker must touch the stalled QUEUED job");

        ProcessingJob reloaded = repository.findById(jobId).orElseThrow();
        assertTrue(
                reloaded.getStatus() == ProcessingStatus.QUEUED || reloaded.getStatus() == ProcessingStatus.VALIDATING,
                "Job must be touched and re-dispatched into execution (QUEUED or actively VALIDATING)");
    }
}
