package com.omragul.knowledge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.knowledge.dto.KnowledgeBaseCreateRequest;
import com.omragul.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.exception.DuplicateResourceException;
import com.omragul.knowledge.exception.GlobalExceptionHandler;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.exception.UnauthorizedException;
import com.omragul.knowledge.security.GatewayAssertionVerifier;
import com.omragul.knowledge.security.InternalAuthService;
import com.omragul.knowledge.security.SecurityAuditLogger;
import com.omragul.knowledge.service.KnowledgeBaseService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KnowledgeBaseController.class)
@Import({ GlobalExceptionHandler.class, InternalAuthService.class, GatewayAssertionVerifier.class, SecurityAuditLogger.class })
class KnowledgeBaseControllerTest {

    private static final String TEST_SECRET = "test-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private GatewayAssertionVerifier gatewayAssertionVerifier;

    private static final KeyPair TEST_KEY_PAIR;
    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            TEST_KEY_PAIR = gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUpVerifier() {
        gatewayAssertionVerifier.setPublicKey(TEST_KEY_PAIR.getPublic());
    }

    private String createAssertion(String userId, String roles, String requestId, String secret, String method, String path) throws Exception {
        long now = Instant.now().getEpochSecond();
        return Jwts.builder()
                .header().type("JWT").and()
                .id(UUID.randomUUID().toString())
                .issuer("gateway-service")
                .audience().single("knowledge-service")
                .subject(userId)
                .claim("roles", roles != null ? roles : "")
                .claim("rid", requestId != null ? requestId : UUID.randomUUID().toString())
                .claim("mth", method)
                .claim("pth", path)
                .issuedAt(Date.from(Instant.ofEpochSecond(now)))
                .expiration(Date.from(Instant.ofEpochSecond(now + 60)))
                .signWith(TEST_KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @Test
    @DisplayName("POST /knowledge-bases - Successfully creates KB from DTO with valid assertion")
    void testCreateKnowledgeBase_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();

        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest("Engineering Docs", "Tech specs", "eng-docs");

        KnowledgeBase created = new KnowledgeBase();
        created.setId(kbId);
        created.setName("Engineering Docs");
        created.setDescription("Tech specs");
        created.setSlug("eng-docs");
        created.setOwnerId(userId);
        created.setStatus(KnowledgeBase.Status.ACTIVE);

        when(knowledgeBaseService.create(any(KnowledgeBase.class), eq(userId))).thenReturn(created);

        mockMvc.perform(post("/knowledge-bases")
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "POST", "/knowledge-bases"))
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(kbId.toString()))
                .andExpect(jsonPath("$.name").value("Engineering Docs"))
                .andExpect(jsonPath("$.ownerId").value(userId.toString()));
    }

    @Test
    @DisplayName("POST /knowledge-bases - Direct untrusted X-User-Id without assertion returns 401 Unauthorized")
    void testCreateKnowledgeBase_DirectUntrustedUserId_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest("Engineering Docs", "Tech specs", "eng-docs");

        mockMvc.perform(post("/knowledge-bases")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Direct client identity headers rejected: gateway assertion required"));
    }

    @Test
    @DisplayName("POST /knowledge-bases - Mismatched assertion and X-User-Id (identity spoofing) returns 401 Unauthorized")
    void testCreateKnowledgeBase_MismatchedAssertionUserId_Returns401() throws Exception {
        UUID realUser = UUID.randomUUID();
        UUID spoofedUser = UUID.randomUUID();
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest("Engineering Docs", "Tech specs", "eng-docs");

        mockMvc.perform(post("/knowledge-bases")
                        .header("X-Gateway-Assertion", createAssertion(realUser.toString(), "ROLE_USER", null, TEST_SECRET, "POST", "/knowledge-bases"))
                        .header("X-User-Id", spoofedUser.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Gateway assertion user ID does not match X-User-Id header"));
    }

    @Test
    @DisplayName("POST /knowledge-bases - Duplicate slug returns 409 Conflict")
    void testCreateKnowledgeBase_DuplicateSlug_Returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest("Engineering Docs", "Tech specs", "eng-docs");

        when(knowledgeBaseService.create(any(KnowledgeBase.class), eq(userId)))
                .thenThrow(new DuplicateResourceException("Knowledge base slug already exists: eng-docs"));

        mockMvc.perform(post("/knowledge-bases")
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "POST", "/knowledge-bases"))
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("PUT /knowledge-bases/{id} - Successfully updates KB via JSON DTO with valid assertion")
    void testUpdateKnowledgeBase_ViaJsonDto() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();

        KnowledgeBaseUpdateRequest updateRequest = new KnowledgeBaseUpdateRequest("Updated KB", "New Description", "updated-kb");

        KnowledgeBase updated = new KnowledgeBase();
        updated.setId(kbId);
        updated.setName("Updated KB");
        updated.setDescription("New Description");
        updated.setSlug("updated-kb");
        updated.setOwnerId(userId);
        updated.setStatus(KnowledgeBase.Status.ACTIVE);

        when(knowledgeBaseService.update(eq(kbId), eq("Updated KB"), eq("New Description"), eq("updated-kb"), eq(userId)))
                .thenReturn(updated);

        mockMvc.perform(put("/knowledge-bases/{id}", kbId)
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "PUT", "/knowledge-bases/" + kbId))
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated KB"))
                .andExpect(jsonPath("$.description").value("New Description"));
    }

    @Test
    @DisplayName("PATCH /knowledge-bases/{id}/restore - Non-owner restore returns 403 Forbidden")
    void testRestoreKnowledgeBase_NonOwner_Returns403() throws Exception {
        UUID attackerId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();

        when(knowledgeBaseService.restore(eq(kbId), eq(attackerId)))
                .thenThrow(new UnauthorizedException("User is not the owner of this knowledge base"));

        mockMvc.perform(patch("/knowledge-bases/{id}/restore", kbId)
                        .header("X-Gateway-Assertion", createAssertion(attackerId.toString(), "ROLE_USER", null, TEST_SECRET, "PATCH", "/knowledge-bases/" + kbId + "/restore"))
                        .header("X-User-Id", attackerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /knowledge-bases/{id}/restore - Missing credentials returns 401 Unauthorized")
    void testRestoreKnowledgeBase_MissingCredentials_Returns401() throws Exception {
        UUID kbId = UUID.randomUUID();

        mockMvc.perform(patch("/knowledge-bases/{id}/restore", kbId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /knowledge-bases/{id} - Success with valid assertion")
    void testGetById_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();

        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(kbId);
        kb.setName("My KB");
        kb.setOwnerId(userId);
        kb.setStatus(KnowledgeBase.Status.ACTIVE);

        when(knowledgeBaseService.getById(eq(kbId), eq(userId))).thenReturn(kb);

        mockMvc.perform(get("/knowledge-bases/{id}", kbId)
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/knowledge-bases/" + kbId))
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(kbId.toString()))
                .andExpect(jsonPath("$.name").value("My KB"));
    }

    @Test
    @DisplayName("GET /knowledge-bases/{id} - Missing credentials returns 401 Unauthorized")
    void testGetById_MissingCredentials_Returns401() throws Exception {
        UUID kbId = UUID.randomUUID();

        mockMvc.perform(get("/knowledge-bases/{id}", kbId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /knowledge-bases/{id} - Unauthorized returns 403 Forbidden")
    void testGetById_Unauthorized_Returns403() throws Exception {
        UUID kbId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();

        when(knowledgeBaseService.getById(eq(kbId), eq(attackerId)))
                .thenThrow(new UnauthorizedException("User does not have access to private knowledge base: " + kbId));

        mockMvc.perform(get("/knowledge-bases/{id}", kbId)
                        .header("X-Gateway-Assertion", createAssertion(attackerId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/knowledge-bases/" + kbId))
                        .header("X-User-Id", attackerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /knowledge-bases/{id} - Soft-deleted KB returns 404 Not Found")
    void testGetById_SoftDeleted_Returns404() throws Exception {
        UUID kbId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(knowledgeBaseService.getById(eq(kbId), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Knowledge base not found: " + kbId));

        mockMvc.perform(get("/knowledge-bases/{id}", kbId)
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/knowledge-bases/" + kbId))
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /knowledge-bases/slug/{slug} - Success with valid assertion")
    void testGetBySlug_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();

        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(kbId);
        kb.setName("My KB");
        kb.setSlug("my-kb");
        kb.setOwnerId(userId);
        kb.setStatus(KnowledgeBase.Status.ACTIVE);

        when(knowledgeBaseService.getBySlug(eq("my-kb"), eq(userId))).thenReturn(kb);

        mockMvc.perform(get("/knowledge-bases/slug/{slug}", "my-kb")
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/knowledge-bases/slug/my-kb"))
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(kbId.toString()))
                .andExpect(jsonPath("$.name").value("My KB"));
    }

    @Test
    @DisplayName("GET /knowledge-bases/slug/{slug} - Missing credentials returns 401 Unauthorized")
    void testGetBySlug_MissingCredentials_Returns401() throws Exception {
        mockMvc.perform(get("/knowledge-bases/slug/{slug}", "my-kb"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /knowledge-bases/{id}/statistics - Success with valid assertion")
    void testGetStatistics_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();

        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(kbId);
        kb.setName("Stats KB");
        kb.setOwnerId(userId);
        kb.setStatus(KnowledgeBase.Status.ACTIVE);
        kb.setTotalDocuments(10);
        kb.setTotalSizeBytes(2048L);

        when(knowledgeBaseService.getActiveById(eq(kbId), eq(userId))).thenReturn(kb);

        mockMvc.perform(get("/knowledge-bases/{id}/statistics", kbId)
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/knowledge-bases/" + kbId + "/statistics"))
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(10))
                .andExpect(jsonPath("$.totalSizeBytes").value(2048));
    }

    @Test
    @DisplayName("GET /knowledge-bases/{id}/statistics - Missing credentials returns 401 Unauthorized")
    void testGetStatistics_MissingCredentials_Returns401() throws Exception {
        UUID kbId = UUID.randomUUID();

        mockMvc.perform(get("/knowledge-bases/{id}/statistics", kbId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("DELETE /knowledge-bases/{id} - Missing credentials returns 401 Unauthorized")
    void testDeleteKnowledgeBase_MissingCredentials_Returns401() throws Exception {
        UUID kbId = UUID.randomUUID();

        mockMvc.perform(delete("/knowledge-bases/{id}", kbId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /knowledge-bases/{id}/archive - Missing credentials returns 401 Unauthorized")
    void testArchiveKnowledgeBase_MissingCredentials_Returns401() throws Exception {
        UUID kbId = UUID.randomUUID();

        mockMvc.perform(patch("/knowledge-bases/{id}/archive", kbId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
