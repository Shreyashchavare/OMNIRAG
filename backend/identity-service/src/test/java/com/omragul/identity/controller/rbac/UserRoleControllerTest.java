package com.omragul.identity.controller.rbac;

import com.omragul.identity.config.SecurityConfig;
import com.omragul.identity.dto.response.rbac.RoleResponseDto;
import com.omragul.identity.enums.RoleType;
import com.omragul.identity.security.JwtAuthenticationEntryPoint;
import com.omragul.identity.security.JwtAuthenticationFilter;
import com.omragul.identity.service.rbac.UserRoleService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserRoleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class UserRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRoleService userRoleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RbacAuthorizationService rbacAuthorizationService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @WithMockUser(username = "69662d91-d592-41e4-8bcf-32718ca29205", authorities = {"ROLE_ASSIGN"})
    @DisplayName("POST /api/v1/users/{userId}/roles/{roleId}: returns 204 No Content when authorized with ROLE_ASSIGN")
    void shouldAssignRoleWhenAuthorized() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/users/{userId}/roles/{roleId}", targetUserId, roleId))
                .andExpect(status().isNoContent());

        verify(userRoleService).assignRole(eq(targetUserId), eq(roleId), eq(UUID.fromString("69662d91-d592-41e4-8bcf-32718ca29205")));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_REVOKE"})
    @DisplayName("DELETE /api/v1/users/{userId}/roles/{roleId}: returns 204 No Content when authorized with ROLE_REVOKE")
    void shouldRevokeRoleWhenAuthorized() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{roleId}", targetUserId, roleId))
                .andExpect(status().isNoContent());

        verify(userRoleService).revokeRole(targetUserId, roleId);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_READ"})
    @DisplayName("GET /api/v1/users/{userId}/roles: returns 200 OK with role list when authorized with ROLE_READ")
    void shouldGetUserRolesWhenAuthorized() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        RoleResponseDto dto = new RoleResponseDto();
        dto.setRoleId(UUID.randomUUID());
        dto.setRoleName("USER");
        dto.setDescription("Standard user");

        when(userRoleService.getUserRoles(targetUserId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/users/{userId}/roles", targetUserId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleName").value("USER"));
    }

    @Test
    @WithMockUser(authorities = {"OTHER_PERMISSION"})
    @DisplayName("POST /api/v1/users/{userId}/roles/{roleId}: returns 403 Forbidden when lacking ROLE_ASSIGN")
    void shouldReturn403WhenLackingAssignPermission() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/users/{userId}/roles/{roleId}", targetUserId, roleId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId}/roles: returns 401 Unauthorized when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{userId}/roles", targetUserId))
                .andExpect(status().isUnauthorized());
    }
}
