package com.omragul.identity.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.identity.config.SecurityConfig;
import com.omragul.identity.dto.request.user.UpdateUserProfileRequestDto;
import com.omragul.identity.dto.response.user.UserProfileResponseDto;
import com.omragul.identity.dto.response.user.UserResponseDto;
import com.omragul.identity.enums.UserStatus;
import com.omragul.identity.security.JwtAuthenticationEntryPoint;
import com.omragul.identity.security.JwtAuthenticationFilter;
import com.omragul.identity.service.security.JwtService;
import com.omragul.identity.service.security.RbacAuthorizationService;
import com.omragul.identity.service.security.UserAuthorizationService;
import com.omragul.identity.service.user.UserProfileService;
import com.omragul.identity.service.user.UserService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RbacAuthorizationService rbacAuthorizationService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean(name = "userAuthorizationService")
    private UserAuthorizationService userAuthorizationService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @WithMockUser(authorities = {"USER_READ"})
    @DisplayName("GET /api/v1/users/{userId}: returns 200 OK when user has USER_READ authority")
    void shouldReturnUserWhenAuthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponseDto dto = new UserResponseDto();
        dto.setUserId(userId);
        dto.setUsername("alice");
        dto.setEmail("alice@omragul.com");
        dto.setStatus(UserStatus.ACTIVE);

        when(userService.getUserById(userId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/users/{userId}", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@omragul.com"));
    }

    @Test
    @WithMockUser(authorities = {"OTHER_AUTHORITY"})
    @DisplayName("GET /api/v1/users/{userId}: returns 403 Forbidden when user lacks USER_READ authority")
    void shouldReturn403WhenLacksUserRead() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}: returns 401 Unauthorized when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"USER_LOCK"})
    @DisplayName("PATCH /api/v1/users/{userId}/lock: returns 204 No Content when authorized")
    void shouldLockUserWhenAuthorized() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/{userId}/lock", userId))
                .andExpect(status().isNoContent());

        verify(userService).lockUser(userId);
    }

    @Test
    @WithMockUser(authorities = {"USER_UNLOCK"})
    @DisplayName("PATCH /api/v1/users/{userId}/unlock: returns 204 No Content when authorized")
    void shouldUnlockUserWhenAuthorized() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/{userId}/unlock", userId))
                .andExpect(status().isNoContent());

        verify(userService).unlockUser(userId);
    }

    @Test
    @WithMockUser(authorities = {"USER_DELETE"})
    @DisplayName("DELETE /api/v1/users/{userId}: returns 204 No Content when authorized")
    void shouldDeleteUserWhenAuthorized() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/{userId}", userId))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(userId);
    }

    @Test
    @WithMockUser(authorities = {"USER_PROFILE_READ"})
    @DisplayName("GET /api/v1/users/{userId}/profile: returns 200 OK when user has USER_PROFILE_READ and owns the profile")
    void shouldReturnProfileWhenAuthorizedAndOwner() throws Exception {
        UUID userId = UUID.randomUUID();
        UserProfileResponseDto dto = new UserProfileResponseDto();
        dto.setFirstName("Alice");
        dto.setLastName("Smith");

        when(userAuthorizationService.canAccessUser(eq(userId), any())).thenReturn(true);
        when(userProfileService.getProfile(userId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/users/{userId}/profile", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
    }

    @Test
    @WithMockUser(authorities = {"USER_PROFILE_READ"})
    @DisplayName("GET /api/v1/users/{userId}/profile: returns 403 Forbidden when user attempts to access another user's profile (BOLA defense)")
    void shouldReturn403WhenAccessingAnotherUserProfile() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        when(userAuthorizationService.canAccessUser(eq(targetUserId), any())).thenReturn(false);

        mockMvc.perform(get("/api/v1/users/{userId}/profile", targetUserId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/profile: returns 401 Unauthorized when unauthenticated")
    void shouldReturn401ForProfileWhenUnauthenticated() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}/profile", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"USER_PROFILE_UPDATE"})
    @DisplayName("PUT /api/v1/users/{userId}/profile: returns 200 OK when user has USER_PROFILE_UPDATE and owns the profile")
    void shouldUpdateProfileWhenAuthorizedAndOwner() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateUserProfileRequestDto request = new UpdateUserProfileRequestDto();
        request.setFirstName("AliceUpdated");
        request.setLastName("SmithUpdated");

        UserProfileResponseDto dto = new UserProfileResponseDto();
        dto.setFirstName("AliceUpdated");
        dto.setLastName("SmithUpdated");

        when(userAuthorizationService.canAccessUser(eq(userId), any())).thenReturn(true);
        when(userProfileService.updateProfile(eq(userId), any(UpdateUserProfileRequestDto.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/users/{userId}/profile", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("AliceUpdated"))
                .andExpect(jsonPath("$.lastName").value("SmithUpdated"));
    }

    @Test
    @WithMockUser(authorities = {"USER_PROFILE_UPDATE"})
    @DisplayName("PUT /api/v1/users/{userId}/profile: returns 403 Forbidden when user attempts to update another user's profile (BOLA defense)")
    void shouldReturn403WhenUpdatingAnotherUserProfile() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UpdateUserProfileRequestDto request = new UpdateUserProfileRequestDto();
        request.setFirstName("Hacker");

        when(userAuthorizationService.canAccessUser(eq(targetUserId), any())).thenReturn(false);

        mockMvc.perform(put("/api/v1/users/{userId}/profile", targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
