package com.omragul.identity.repository;

import com.omragul.identity.entity.auth.Otp;
import com.omragul.identity.enums.OtpPurpose;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends CrudRepository<Otp, UUID> {

    List<Otp> findByUserUserIdAndPurpose(
            UUID userId,
            OtpPurpose purpose
    );

    List<Otp> findByUserUserIdAndPurposeAndVerifiedFalse(
            UUID userId,
            OtpPurpose purpose
    );

    List<Otp> findByUserUserIdAndPurposeAndVerifiedFalseAndExpiresAtAfter(
            UUID userId,
            OtpPurpose purpose,
            Instant currentTime
    );

    Optional<Otp> findTopByUserUserIdAndPurposeOrderByCreatedAtDesc(
            UUID userId,
            OtpPurpose purpose
    );
}