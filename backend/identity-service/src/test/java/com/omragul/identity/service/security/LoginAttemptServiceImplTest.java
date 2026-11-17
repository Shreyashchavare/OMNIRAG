package com.omragul.identity.service.security;

import com.omragul.identity.entity.user.User;
import com.omragul.identity.enums.UserStatus;
import com.omragul.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LoginAttemptServiceImpl loginAttemptService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(loginAttemptService, "maxFailedLoginAttempts", 5);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("recordFailedLoginAttempt: handles null failedLoginAttempts gracefully without NPE")
    void shouldHandleNullFailedLoginAttempts() {
        User user = User.builder()
                .userId(userId)
                .username("testuser")
                .failedLoginAttempts(null) // null in DB
                .accountLocked(false)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        loginAttemptService.recordFailedLoginAttempt(userId);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getAccountLocked()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("recordFailedLoginAttempt: locks account when failed attempts reach threshold")
    void shouldLockAccountWhenThresholdReached() {
        User user = User.builder()
                .userId(userId)
                .username("testuser")
                .failedLoginAttempts(4)
                .accountLocked(false)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        loginAttemptService.recordFailedLoginAttempt(userId);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getAccountLocked()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("recordSuccessfulLogin: resets failed login attempts to 0 and updates lastLoginAt")
    void shouldResetFailedAttemptsOnSuccess() {
        User user = User.builder()
                .userId(userId)
                .username("testuser")
                .failedLoginAttempts(3)
                .build();

        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        loginAttemptService.recordSuccessfulLogin(userId);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);
    }
}
