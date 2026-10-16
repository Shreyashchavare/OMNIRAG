package com.omragul.identity.service.security;

import com.omragul.identity.entity.user.User;
import com.omragul.identity.enums.UserStatus;
import com.omragul.identity.exception.ResourceNotFoundException;
import com.omragul.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final UserRepository userRepository;

    @Value("${identity.security.max-failed-login-attempts}")
    private int maxFailedLoginAttempts;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLoginAttempt(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        int failedAttempts = user.getFailedLoginAttempts() + 1;

        user.setFailedLoginAttempts(failedAttempts);

        if (failedAttempts >= maxFailedLoginAttempts) {
            user.setAccountLocked(true);
            user.setStatus(UserStatus.LOCKED);
        }

        userRepository.save(user);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulLogin(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(Instant.now());

        userRepository.save(user);
    }
}