package com.omragul.identity.repository;

import com.omragul.identity.entity.audit.UserSession;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends CrudRepository<UserSession, UUID> {

    List<UserSession> findByUserUserId(UUID userId);

    List<UserSession> findByUserUserIdAndActiveTrue(UUID userId);

    Optional<UserSession> findByJwtId(String jwtId);

    Optional<UserSession> findByJwtIdAndActiveTrue(String jwtId);
}