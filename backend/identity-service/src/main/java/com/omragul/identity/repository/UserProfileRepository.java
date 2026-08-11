package com.omragul.identity.repository;

import com.omragul.identity.entity.user.UserProfile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends CrudRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserUserId(UUID userId);

    boolean existsByUserUserId(UUID userId);
}
