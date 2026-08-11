package com.omragul.identity.repository;

import com.omragul.identity.entity.auth.PasswordHistory;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordHistoryRepository extends CrudRepository<PasswordHistory, UUID> {

    List<PasswordHistory> findByUserUserIdOrderByChangedAtDesc(UUID userId);

    Optional<PasswordHistory> findTopByUserUserIdOrderByChangedAtDesc(UUID userId);
}