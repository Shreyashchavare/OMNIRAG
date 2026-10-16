package com.omragul.identity.service.auth;

import com.omragul.identity.dto.request.auth.LoginRequestDto;
import com.omragul.identity.dto.request.auth.SignupRequestDto;
import com.omragul.identity.dto.response.auth.LoginResponseDto;
import com.omragul.identity.dto.response.auth.SignupResponseDto;
import com.omragul.identity.dto.response.auth.TokenResponseDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.entity.auth.RefreshToken;
import com.omragul.identity.entity.rbac.Role;
import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.entity.user.User;
import com.omragul.identity.enums.RoleType;
import com.omragul.identity.enums.UserStatus;
import com.omragul.identity.exception.IdentityException;
import com.omragul.identity.exception.UserAlreadyExistsException;
import com.omragul.identity.exception.UserLockedException;
import com.omragul.identity.mapper.UserMapper;
import com.omragul.identity.repository.RoleRepository;
import com.omragul.identity.repository.UserRoleRepository;
import com.omragul.identity.service.security.JwtService;
import com.omragul.identity.service.security.LoginAttemptService;
import com.omragul.identity.service.security.PasswordService;
import com.omragul.identity.service.security.RefreshTokenService;
import com.omragul.identity.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final PasswordService passwordService;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    @Value("${identity.jwt.expiration-minutes}")
    private long jwtExpirationMinutes;

    @Override
    @Transactional
    public SignupResponseDto signup(SignupRequestDto request) {

        // 1. Check whether username already exists
        if (userService.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(
                    "Username already exists: " + request.getUsername()
            );
        }

        // 2. Check whether email already exists
        if (userService.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "Email already exists: " + request.getEmail()
            );
        }

        // 3. Hash password
        String passwordHash =
                passwordService.hashPassword(request.getPassword());

        // 4. Create user + profile
        User user = userService.createUser(
                request.getUsername(),
                request.getEmail(),
                passwordHash,
                request.getProfile()
        );

        // 5. Assign default USER role
        Role userRole = roleRepository
                .findByRoleName(RoleType.USER)
                .orElseThrow(() ->
                        new IdentityException(
                                "Default USER role not found"
                        )
                );

        UserRole userRoleMapping = UserRole.builder()
                .user(user)
                .role(userRole)
                .assignedBy(null)
                .build();

        userRoleRepository.save(userRoleMapping);

        // 6. Convert user entity to response
        UserResponseDto userResponse =
                userMapper.toResponseDto(user);

        return new SignupResponseDto(
                "User registered successfully",
                userResponse
        );
    }

    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {

        User user;

        // 1. Find user by username or email
        if (request.getIdentifier().contains("@")) {

            user = userService.getUserEntityByEmail(
                    request.getIdentifier()
            );

        } else {

            user = userService.getUserEntityByUsername(
                    request.getIdentifier()
            );
        }

        // 2. Check whether account is locked
        if (Boolean.TRUE.equals(user.getAccountLocked())
                || user.getStatus() == UserStatus.LOCKED) {

            throw new UserLockedException(
                    "User account is locked"
            );
        }

        // 3. Check account status
        if (user.getStatus() != UserStatus.ACTIVE) {

            throw new IdentityException(
                    "User account is not active"
            );
        }

        // 4. Verify password
        boolean passwordMatches = passwordService.matches(
                request.getPassword(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {

            loginAttemptService.recordFailedLoginAttempt(
                    user.getUserId()
            );

            throw new IdentityException(
                    "Invalid username/email or password"
            );
        }

        loginAttemptService.recordSuccessfulLogin(
                user.getUserId()
        );


        // 5. Generate access token
        String accessToken =
                jwtService.generateAccessToken(user);

        // 6. Generate refresh token
        String refreshToken =
                refreshTokenService.createToken(
                        user.getUserId(),
                        null,
                        null,
                        null
                );

        // 7. Map user to response DTO
        UserResponseDto userResponse =
                userMapper.toResponseDto(user);

        // 8. Build login response
        return new LoginResponseDto(
                accessToken,
                refreshToken,
                "Bearer",
                jwtExpirationMinutes * 60L,
                userResponse
        );
    }

    @Override
    @Transactional
    public TokenResponseDto refreshAccessToken(String refreshToken) {

        // <----Refresh token rotation--->//
        // 1.Validate the refresh token
        RefreshToken storedRefreshToken = refreshTokenService.validateToken(refreshToken);

        // 2.Get the user associated with the refresh token
        User user = storedRefreshToken.getUser();

        // 3.Generate new JWT access token
        String accessToken = jwtService.generateAccessToken(user);

        // 4.Revoke the old refresh token
        refreshTokenService.revokeToken(refreshToken);

        // 5.Generate a new refresh token
        String newRefreshToken =
                refreshTokenService.createToken(
                        user.getUserId(),
                        storedRefreshToken.getDeviceName(),
                        storedRefreshToken.getIpAddress(),
                        storedRefreshToken.getUserAgent()
                );

        // 6.Return both new tokens
        return new TokenResponseDto(
                accessToken,
                refreshToken,
                "Bearer",
                jwtExpirationMinutes * 60L
        );
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {

        refreshTokenService.revokeToken(refreshToken);
    }

    @Override
    @Transactional
    public void logoutAll(UUID userId) {

        refreshTokenService.revokeAllUserTokens(userId);
    }
}