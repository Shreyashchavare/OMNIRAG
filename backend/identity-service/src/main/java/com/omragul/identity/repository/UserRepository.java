package com.omragul.identity.repository;

import com.omragul.identity.entity.user.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends CrudRepository<User, UUID> {

    // Lookup
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    // Existence checks
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Active user lookup
    Optional<User> findByUsernameAndIsDeletedFalse(String username);

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    Optional<User> findByUserIdAndIsDeletedFalse(UUID userId);

    // Active user existence checks
    boolean existsByUsernameAndIsDeletedFalse(String username);

    boolean existsByEmailAndIsDeletedFalse(String email);
}
