package com.omragul.identity.controller.auth;

import com.omragul.identity.config.SecurityConfig;
import com.omragul.identity.dto.request.auth.LoginRequestDto;
import com.omragul.identity.dto.request.auth.RefreshTokenRequestDto;
import com.omragul.identity.dto.request.auth.SignupRequestDto;
import com.omragul.identity.dto.response.auth.LoginResponseDto;
import com.omragul.identity.dto.response.auth.SignupResponseDto;
import com.omragul.identity.dto.response.auth.TokenResponseDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.security.JwtAuthenticationEntryPoint;
import com.omragul.identity.security.JwtAuthenticationFilter;
import com.omragul.identity.service.auth.AuthService;
import com.omragul.identity.service.security.JwtService;
import com.omragul.identity.service.security.RbacAuthorizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RbacAuthorizationService rbacAuthorizationService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("POST /api/v1/auth/signup: returns 201 Created on valid request")
    void shouldReturn201OnSignup() throws Exception {
        SignupRequestDto request = new SignupRequestDto("testuser", "test@omragul.com", "Password123!", null);
        UserResponseDto userDto = new UserResponseDto();
        userDto.setUserId(UUID.randomUUID());
        userDto.setUsername("testuser");
        userDto.setEmail("test@omragul.com");
        SignupResponseDto response = new SignupResponseDto("User registered successfully", userDto);

        when(authService.signup(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.user.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/signup: returns 400 Bad Request on validation failure (empty username)")
    void shouldReturn400OnInvalidSignup() throws Exception {
        SignupRequestDto invalidRequest = new SignupRequestDto("", "invalid-email", "short", null);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: returns 200 OK with tokens on valid login")
    void shouldReturn200OnLogin() throws Exception {
        LoginRequestDto request = new LoginRequestDto("test@omragul.com", "Password123!");
        UserResponseDto userDto = new UserResponseDto();
        userDto.setUserId(UUID.randomUUID());
        userDto.setUsername("testuser");
        LoginResponseDto response = new LoginResponseDto("access_tok", "refresh_tok", "Bearer", 900L, userDto);

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_tok"))
                .andExpect(jsonPath("$.refreshToken").value("refresh_tok"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh: returns 200 OK with refreshed tokens")
    void shouldReturn200OnRefresh() throws Exception {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto("valid_refresh_token");
        TokenResponseDto response = new TokenResponseDto("new_acc_tok", "new_ref_tok", "Bearer", 900L);

        when(authService.refreshAccessToken(eq("valid_refresh_token"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new_acc_tok"))
                .andExpect(jsonPath("$.refreshToken").value("new_ref_tok"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/auth/logout: returns 204 No Content for authenticated request")
    void shouldReturn204OnLogout() throws Exception {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto("token_to_logout");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).logout("token_to_logout");
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout: returns 401 Unauthorized for unauthenticated request")
    void shouldReturn401OnUnauthenticatedLogout() throws Exception {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto("token_to_logout");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
