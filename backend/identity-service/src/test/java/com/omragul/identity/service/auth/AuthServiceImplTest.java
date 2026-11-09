package com.omragul.identity.service.auth;

import com.omragul.identity.dto.request.auth.LoginRequestDto;
import com.omragul.identity.dto.request.auth.SignupRequestDto;
import com.omragul.identity.dto.request.user.SignupProfileRequestDto;
import com.omragul.identity.dto.response.auth.LoginResponseDto;
import com.omragul.identity.dto.response.auth.SignupResponseDto;
import com.omragul.identity.dto.response.auth.TokenResponseDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.entity.auth.RefreshToken;
import com.omragul.identity.entity.rbac.Role;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private PasswordService passwordService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private UUID testUserId;
    private Role defaultUserRole;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpirationMinutes", 15L);

        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .userId(testUserId)
                .username("johndoe")
                .email("johndoe@example.com")
                .passwordHash("hashed_secret")
                .status(UserStatus.ACTIVE)
                .accountLocked(false)
                .build();

        defaultUserRole = Role.builder()
                .roleId(UUID.randomUUID())
                .roleName(RoleType.USER)
                .build();
    }

    @Test
    @DisplayName("Signup: should register user successfully and assign default USER role")
    void shouldSignupSuccessfully() {
        SignupProfileRequestDto profileDto = new SignupProfileRequestDto();
        SignupRequestDto request = new SignupRequestDto("johndoe", "johndoe@example.com", "Password123!", profileDto);

        when(userService.existsByUsername("johndoe")).thenReturn(false);
        when(userService.existsByEmail("johndoe@example.com")).thenReturn(false);
        when(passwordService.hashPassword("Password123!")).thenReturn("hashed_secret");
        when(userService.createUser(eq("johndoe"), eq("johndoe@example.com"), eq("hashed_secret"), any()))
                .thenReturn(testUser);
        when(roleRepository.findByRoleName(RoleType.USER)).thenReturn(Optional.of(defaultUserRole));

        UserResponseDto userResponseDto = new UserResponseDto();
        userResponseDto.setUserId(testUserId);
        userResponseDto.setUsername("johndoe");
        when(userMapper.toResponseDto(testUser)).thenReturn(userResponseDto);

        SignupResponseDto response = authService.signup(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("User registered successfully");
        assertThat(response.getUser().getUsername()).isEqualTo("johndoe");
        verify(userRoleRepository).save(any());
    }

    @Test
    @DisplayName("Signup: should throw UserAlreadyExistsException when username is taken")
    void shouldThrowWhenUsernameExists() {
        SignupRequestDto request = new SignupRequestDto("johndoe", "johndoe@example.com", "Password123!", null);
        when(userService.existsByUsername("johndoe")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username already exists");

        verify(userService, never()).createUser(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Login: should log in successfully with valid credentials")
    void shouldLoginSuccessfully() {
        LoginRequestDto request = new LoginRequestDto("johndoe@example.com", "Password123!");

        when(userService.getUserEntityByEmail("johndoe@example.com")).thenReturn(testUser);
        when(passwordService.matches("Password123!", "hashed_secret")).thenReturn(true);
        when(jwtService.generateAccessToken(testUser)).thenReturn("access_token_jwt");
        when(refreshTokenService.createToken(testUserId, null, null, null)).thenReturn("raw_refresh_token");

        UserResponseDto userResponseDto = new UserResponseDto();
        userResponseDto.setUserId(testUserId);
        when(userMapper.toResponseDto(testUser)).thenReturn(userResponseDto);

        LoginResponseDto response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access_token_jwt");
        assertThat(response.getRefreshToken()).isEqualTo("raw_refresh_token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900L);

        verify(loginAttemptService).recordSuccessfulLogin(testUserId);
    }

    @Test
    @DisplayName("Login: should record failed attempt and throw when password is wrong")
    void shouldRecordFailedAttemptOnInvalidPassword() {
        LoginRequestDto request = new LoginRequestDto("johndoe", "WrongPassword");

        when(userService.getUserEntityByUsername("johndoe")).thenReturn(testUser);
        when(passwordService.matches("WrongPassword", "hashed_secret")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IdentityException.class)
                .hasMessageContaining("Invalid username/email or password");

        verify(loginAttemptService).recordFailedLoginAttempt(testUserId);
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("Login: should throw UserLockedException when user account is locked")
    void shouldThrowWhenAccountLocked() {
        testUser.setAccountLocked(true);
        LoginRequestDto request = new LoginRequestDto("johndoe", "Password123!");

        when(userService.getUserEntityByUsername("johndoe")).thenReturn(testUser);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UserLockedException.class)
                .hasMessageContaining("User account is locked");
    }

    @Test
    @DisplayName("Refresh: should rotate refresh token and issue new access token")
    void shouldRefreshAccessTokenSuccessfully() {
        RefreshToken storedToken = RefreshToken.builder()
                .user(testUser)
                .deviceName("Chrome")
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .build();

        when(refreshTokenService.validateToken("old_refresh_token")).thenReturn(storedToken);
        when(jwtService.generateAccessToken(testUser)).thenReturn("new_access_token");
        when(refreshTokenService.createToken(testUserId, "Chrome", "127.0.0.1", "Mozilla/5.0"))
                .thenReturn("new_refresh_token");

        TokenResponseDto response = authService.refreshAccessToken("old_refresh_token");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new_access_token");
        assertThat(response.getRefreshToken()).isEqualTo("new_refresh_token");
        verify(refreshTokenService).revokeToken("old_refresh_token");
    }

    @Test
    @DisplayName("Logout: should revoke token")
    void shouldLogout() {
        authService.logout("refresh_token_to_revoke");
        verify(refreshTokenService).revokeToken("refresh_token_to_revoke");
    }

    @Test
    @DisplayName("LogoutAll: should revoke all user tokens")
    void shouldLogoutAll() {
        authService.logoutAll(testUserId);
        verify(refreshTokenService).revokeAllUserTokens(testUserId);
    }
}

