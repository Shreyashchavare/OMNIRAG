package com.omragul.identity.service.user;

import com.omragul.identity.dto.request.user.UpdateUserRequestDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.enums.UserStatus;
import com.omragul.identity.exception.ResourceNotFoundException;
import com.omragul.identity.exception.UserAlreadyExistsException;
import com.omragul.identity.mapper.UserMapper;
import com.omragul.identity.mapper.UserProfileMapper;
import com.omragul.identity.repository.RoleRepository;
import com.omragul.identity.repository.UserRepository;
import com.omragul.identity.repository.UserRoleRepository;
import com.omragul.identity.service.security.PasswordService;
import com.omragul.identity.service.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .userId(userId)
                .username("john_doe")
                .email("john@example.com")
                .status(UserStatus.ACTIVE)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .build();
        user.setIsDeleted(false);
    }

    @Test
    @DisplayName("lockUser: sets accountLocked to true, LOCKED status, and revokes all refresh tokens")
    void shouldLockUserAndRevokeAllTokens() {
        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        userService.lockUser(userId);

        assertThat(user.getAccountLocked()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllUserTokens(userId);
    }

    @Test
    @DisplayName("deleteUser: sets isDeleted to true, deletedAt, and revokes all refresh tokens")
    void shouldDeleteUserAndRevokeAllTokens() {
        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        userService.deleteUser(userId);

        assertThat(user.getIsDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllUserTokens(userId);
    }

    @Test
    @DisplayName("updateUser: throws UserAlreadyExistsException when new username belongs to another user")
    void shouldThrowWhenUpdatingToExistingUsername() {
        UpdateUserRequestDto request = new UpdateUserRequestDto("existing_user", "john@example.com");
        UUID otherUserId = UUID.randomUUID();
        User existingOtherUser = User.builder().userId(otherUserId).username("existing_user").build();

        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByUsernameAndIsDeletedFalse("existing_user")).thenReturn(Optional.of(existingOtherUser));

        assertThatThrownBy(() -> userService.updateUser(userId, request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser: throws UserAlreadyExistsException when new email belongs to another user")
    void shouldThrowWhenUpdatingToExistingEmail() {
        UpdateUserRequestDto request = new UpdateUserRequestDto("john_doe", "taken@example.com");
        UUID otherUserId = UUID.randomUUID();
        User existingOtherUser = User.builder().userId(otherUserId).email("taken@example.com").build();

        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailAndIsDeletedFalse("taken@example.com")).thenReturn(Optional.of(existingOtherUser));

        assertThatThrownBy(() -> userService.updateUser(userId, request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser: successfully updates when username/email are unchanged or non-colliding")
    void shouldUpdateSuccessfullyWhenNonColliding() {
        UpdateUserRequestDto request = new UpdateUserRequestDto("john_doe", "john_new@example.com");

        when(userRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailAndIsDeletedFalse("john_new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);

        UserResponseDto responseDto = new UserResponseDto();
        responseDto.setUserId(userId);
        responseDto.setUsername("john_doe");
        responseDto.setEmail("john_new@example.com");
        when(userMapper.toResponseDto(user)).thenReturn(responseDto);

        UserResponseDto result = userService.updateUser(userId, request);

        assertThat(result).isNotNull();
        verify(userMapper).updateUser(request, user);
        verify(userRepository).save(user);
    }
}
