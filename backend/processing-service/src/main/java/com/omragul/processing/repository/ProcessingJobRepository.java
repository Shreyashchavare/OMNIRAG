package com.omragul.processing.repository;

import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    List<ProcessingJob> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    Optional<ProcessingJob> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    Optional<ProcessingJob> findByIdempotencyKey(String idempotencyKey);

    Optional<ProcessingJob> findByDocumentIdAndIdempotencyKey(UUID documentId, String idempotencyKey);

    boolean existsByDocumentIdAndStatusIn(UUID documentId, Collection<ProcessingStatus> statuses);

    Optional<ProcessingJob> findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(UUID documentId, Collection<ProcessingStatus> statuses);

    Page<ProcessingJob> findByStatus(ProcessingStatus status, Pageable pageable);

    Page<ProcessingJob> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<ProcessingJob> findByOwnerIdAndStatus(UUID ownerId, ProcessingStatus status, Pageable pageable);

    Optional<ProcessingJob> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<ProcessingJob> findFirstByDocumentIdAndOwnerIdOrderByCreatedAtDesc(UUID documentId, UUID ownerId);

    List<ProcessingJob> findByStatusIn(Collection<ProcessingStatus> statuses);

    /**
     * PROC-13: Atomic single-statement conditional claim query.
     * Transitions a job from an expected status (e.g. QUEUED) to a target status (e.g. VALIDATING)
     * while incrementing attemptCount and version, and updating timestamps.
     * Evaluates atomically under database row-level locking without holding locks during thread sleeps.
     *
     * @return Number of rows updated (1 if successfully claimed, 0 if already claimed or not found)
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProcessingJob j SET " +
            "j.status = :targetStatus, " +
            "j.stage = :targetStage, " +
            "j.attemptCount = j.attemptCount + 1, " +
            "j.currentStep = :currentStep, " +
            "j.startedAt = COALESCE(j.startedAt, :now), " +
            "j.updatedAt = :now, " +
            "j.version = j.version + 1 " +
            "WHERE j.id = :jobId AND j.status = :expectedStatus")
    int claimJobAtomic(
            @Param("jobId") UUID jobId,
            @Param("expectedStatus") ProcessingStatus expectedStatus,
            @Param("targetStatus") ProcessingStatus targetStatus,
            @Param("targetStage") ProcessingStage targetStage,
            @Param("currentStep") String currentStep,
            @Param("now") Instant now
    );

    /**
     * PROC-03: Finds IDs of stale in-flight jobs older than the given threshold,
     * bounded by pagination to prevent unbounded batch sizes.
     */
    @Query("SELECT j.id FROM ProcessingJob j WHERE j.status IN :inFlightStatuses AND j.updatedAt < :staleThreshold ORDER BY j.updatedAt ASC")
    List<UUID> findStaleInFlightJobIds(
            @Param("inFlightStatuses") Collection<ProcessingStatus> inFlightStatuses,
            @Param("staleThreshold") Instant staleThreshold,
            Pageable pageable
    );

    /**
     * PROC-03: Finds IDs of stalled QUEUED jobs older than the given threshold,
     * bounded by pagination.
     */
    @Query("SELECT j.id FROM ProcessingJob j WHERE j.status = :queuedStatus AND j.updatedAt < :staleThreshold ORDER BY j.updatedAt ASC")
    List<UUID> findStaleQueuedJobIds(
            @Param("queuedStatus") ProcessingStatus queuedStatus,
            @Param("staleThreshold") Instant staleThreshold,
            Pageable pageable
    );

    /**
     * PROC-03: Concurrency-safe atomic recovery for a stale in-flight job under attempt limit.
     * Transitions status from IN_FLIGHT to QUEUED only if status is still in IN_FLIGHT
     * and updatedAt is still older than staleThreshold.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProcessingJob j SET " +
            "j.status = :targetStatus, " +
            "j.stage = :targetStage, " +
            "j.currentStep = :currentStep, " +
            "j.updatedAt = :now, " +
            "j.version = j.version + 1 " +
            "WHERE j.id = :jobId AND j.status IN :inFlightStatuses AND j.updatedAt < :staleThreshold AND j.attemptCount < j.maxAttempts")
    int recoverStaleInFlightJobAtomic(
            @Param("jobId") UUID jobId,
            @Param("inFlightStatuses") Collection<ProcessingStatus> inFlightStatuses,
            @Param("staleThreshold") Instant staleThreshold,
            @Param("targetStatus") ProcessingStatus targetStatus,
            @Param("targetStage") ProcessingStage targetStage,
            @Param("currentStep") String currentStep,
            @Param("now") Instant now
    );

    /**
     * PROC-03: Concurrency-safe atomic transition to FAILED for a stale in-flight job that has exhausted max attempts.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProcessingJob j SET " +
            "j.status = :failedStatus, " +
            "j.stage = :failedStage, " +
            "j.errorCode = :errorCode, " +
            "j.errorMessage = :errorMessage, " +
            "j.currentStep = :currentStep, " +
            "j.completedAt = :now, " +
            "j.updatedAt = :now, " +
            "j.version = j.version + 1 " +
            "WHERE j.id = :jobId AND j.status IN :inFlightStatuses AND j.updatedAt < :staleThreshold AND j.attemptCount >= j.maxAttempts")
    int failStaleExhaustedJobAtomic(
            @Param("jobId") UUID jobId,
            @Param("inFlightStatuses") Collection<ProcessingStatus> inFlightStatuses,
            @Param("staleThreshold") Instant staleThreshold,
            @Param("failedStatus") ProcessingStatus failedStatus,
            @Param("failedStage") ProcessingStage failedStage,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("currentStep") String currentStep,
            @Param("now") Instant now
    );

    /**
     * PROC-03: Concurrency-safe touch and claim for a stale QUEUED job before re-dispatching.
     * Updates updatedAt to prevent competing instances from double-dispatching.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProcessingJob j SET " +
            "j.currentStep = :currentStep, " +
            "j.updatedAt = :now, " +
            "j.version = j.version + 1 " +
            "WHERE j.id = :jobId AND j.status = :queuedStatus AND j.updatedAt < :staleThreshold")
    int touchStaleQueuedJobAtomic(
            @Param("jobId") UUID jobId,
            @Param("queuedStatus") ProcessingStatus queuedStatus,
            @Param("staleThreshold") Instant staleThreshold,
            @Param("currentStep") String currentStep,
            @Param("now") Instant now
    );
}
