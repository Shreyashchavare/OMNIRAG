package com.omragul.identity.service.user;

import com.omragul.identity.dto.request.user.UpdateUserRequestDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.exception.ResourceNotFoundException;
import com.omragul.identity.mapper.UserMapper;
import com.omragul.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponseDto getUserById(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        return userMapper.toResponseDto(user);
    }

    @Override
    public UserResponseDto getUserByUsername(String username) {

        User user = userRepository
                .findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with username: " + username
                        )
                );

        return userMapper.toResponseDto(user);
    }

    @Override
    public UserResponseDto getUserByEmail(String email) {

        User user = userRepository
                .findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with email: " + email
                        )
                );

        return userMapper.toResponseDto(user);
    }

    @Override
    @Transactional
    public UserResponseDto updateUser(
            UUID userId,
            UpdateUserRequestDto request
    ) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );
        userMapper.updateUser(request, user);

        User updatedUser = userRepository.save(user);

        return userMapper.toResponseDto(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        user.setIsDeleted(true);
        user.setDeletedAt(Instant.now());

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void restoreUser(UUID userId) {
        User user = userRepository
                .findByUserIdAndIsDeletedTrue(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Deleted user not found with id: " + userId
                        )
                );

        user.setIsDeleted(false);
        user.setDeletedAt(null);
        user.setDeletedBy(null);

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void lockUser(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        user.setAccountLocked(true);

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void unlockUser(UUID userId) {

        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        user.setAccountLocked(false);

        userRepository.save(user);
    }
}