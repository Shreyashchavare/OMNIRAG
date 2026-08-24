package com.omragul.identity.repository;

import com.omragul.identity.entity.auth.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserUserId(UUID userId);

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(
            String tokenHash
    );

    Optional<RefreshToken> findByTokenHashAndRevokedFalseAndExpiresAtAfter(
            String tokenHash,
            Instant currentTime
    );
}


