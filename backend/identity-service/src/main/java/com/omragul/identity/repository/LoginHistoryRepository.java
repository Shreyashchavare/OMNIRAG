package com.omragul.identity.repository;

import com.omragul.identity.entity.audit.LoginHistory;
import com.omragul.identity.enums.LoginStatus;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginHistoryRepository extends CrudRepository<LoginHistory, UUID> {

    List<LoginHistory> findByUserUserIdOrderByLoginTimeDesc(UUID userId);

    Optional<LoginHistory> findTopByUserUserIdOrderByLoginTimeDesc(UUID userId);

    List<LoginHistory> findByUserUserIdAndLoginStatusOrderByLoginTimeDesc(
            UUID userId,
            LoginStatus loginStatus
    );
}