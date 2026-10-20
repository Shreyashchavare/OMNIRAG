package com.omragul.processing.repository;

import com.omragul.processing.entity.ProcessingJob;
import com.omragul.processing.enums.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    List<ProcessingJob> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    Optional<ProcessingJob> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    Optional<ProcessingJob> findByIdempotencyKey(String idempotencyKey);

    boolean existsByDocumentIdAndStatusIn(UUID documentId, Collection<ProcessingStatus> statuses);

    Optional<ProcessingJob> findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(UUID documentId, Collection<ProcessingStatus> statuses);

    Page<ProcessingJob> findByStatus(ProcessingStatus status, Pageable pageable);

    List<ProcessingJob> findByStatusIn(Collection<ProcessingStatus> statuses);
}
