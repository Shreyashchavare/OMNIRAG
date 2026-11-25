package com.omragul.knowledge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.knowledge.dto.DocumentRequest;
import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.repository.DocumentRepository;
import com.omragul.knowledge.repository.KnowledgeBaseRepository;
import com.omragul.knowledge.security.GatewayAssertionVerifier;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import com.omragul.knowledge.security.GatewayAssertionReplayService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentSecurityIntegrationTest {

    private static final String TEST_SECRET = "test-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayAssertionReplayService replayService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID userA;
    private UUID userB;
    private KnowledgeBase privateKbA;

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
    void setUp() {
        when(replayService.claimJti(any(), anyLong(), any(), any())).thenReturn(true);
        gatewayAssertionVerifier.setPublicKey(TEST_KEY_PAIR.getPublic());
        documentRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();

        userA = UUID.randomUUID();
        userB = UUID.randomUUID();

        privateKbA = new KnowledgeBase();
        privateKbA.setName("Confidential KB");
        privateKbA.setSlug("confidential-kb-" + UUID.randomUUID().toString().substring(0, 8));
        privateKbA.setOwnerId(userA);
        privateKbA.setCreatedBy(userA);
        privateKbA.setUpdatedBy(userA);
        privateKbA.setVisibility(KnowledgeBase.Visibility.PRIVATE);
        privateKbA.setStatus(KnowledgeBase.Status.ACTIVE);
        privateKbA.setCreatedAt(LocalDateTime.now());
        privateKbA.setUpdatedAt(LocalDateTime.now());
        privateKbA = knowledgeBaseRepository.save(privateKbA);
    }

    private String createAssertion(UUID userId, String roles, String method, String path) throws Exception {
        long now = Instant.now().getEpochSecond();
        return Jwts.builder()
                .header().type("JWT").and()
                .id(UUID.randomUUID().toString())
                .issuer("gateway-service")
                .audience().single("knowledge-service")
                .subject(userId.toString())
                .claim("roles", roles != null ? roles : "ROLE_USER")
                .claim("rid", UUID.randomUUID().toString())
                .claim("mth", method)
                .claim("pth", path)
                .issuedAt(Date.from(Instant.ofEpochSecond(now)))
                .expiration(Date.from(Instant.ofEpochSecond(now + 60)))
                .signWith(TEST_KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @Test
    @DisplayName("Integration: Controller -> Real Service -> DB User Isolation with Verified Gateway Assertion")
    void testRealService_UserIsolation() throws Exception {
        // 0. Spoofing attempt without assertion -> 401 Unauthorized
        DocumentRequest request = new DocumentRequest();
        request.setKnowledgeBaseId(privateKbA.getId());
        request.setName("TradeSecrets.pdf");
        request.setOriginalFilename("tradesecrets.pdf");
        request.setStoredFilename("stored_tradesecrets.pdf");
        request.setStoragePath("/data/docs/tradesecrets.pdf");
        request.setMimeType("application/pdf");
        request.setFileSizeBytes(4096L);
        request.setChecksum("sha256-abc1234567890");

        mockMvc.perform(post("/api/v1/documents")
                        .header("X-User-Id", userA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Direct client identity headers rejected: gateway assertion required"));

        // 1. User A creates document with valid assertion -> 200 OK
        String responseContent = mockMvc.perform(post("/api/v1/documents")
                        .header("X-Gateway-Assertion", createAssertion(userA, "ROLE_USER", "POST", "/api/v1/documents"))
                        .header("X-User-Id", userA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TradeSecrets.pdf"))
                .andExpect(jsonPath("$.ownerId").value(userA.toString()))
                .andReturn().getResponse().getContentAsString();

        UUID docId = UUID.fromString(objectMapper.readTree(responseContent).get("id").asText());

        // 2. User B tries to read User A's document with valid User B assertion -> 403 Forbidden
        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-Gateway-Assertion", createAssertion(userB, "ROLE_USER", "GET", "/api/v1/documents/" + docId))
                        .header("X-User-Id", userB.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // 3. User A reads their own document with valid User A assertion -> 200 OK
        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-Gateway-Assertion", createAssertion(userA, "ROLE_USER", "GET", "/api/v1/documents/" + docId))
                        .header("X-User-Id", userA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()));
    }

    @Test
    @DisplayName("Integration: Internal Authentication with X-Internal-Secret at actual HTTP endpoint")
    void testRealEndpoint_InternalAuthentication() throws Exception {
        // Pre-create document
        Document doc = new Document();
        doc.setKnowledgeBaseId(privateKbA.getId());
        doc.setName("PipelineDoc.pdf");
        doc.setOriginalFilename("pipeline.pdf");
        doc.setStoredFilename("stored_pipeline.pdf");
        doc.setStoragePath("/data/pipeline.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(1024L);
        doc.setChecksum("sha256-pipeline123");
        doc.setOwnerId(userA);
        doc.setProcessingStatus(Document.ProcessingStatus.PENDING);
        doc.setStatus(Document.Status.ACTIVE);
        doc.setCurrentVersion(1);
        doc.setVersion(1);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc = documentRepository.save(doc);

        UUID docId = doc.getId();

        // 1. Internal pipeline with configured test secret -> 200 OK
        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()));

        // 2. Caller with invalid internal secret -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-Internal-Secret", "unauthorized-secret-attempt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        // 3. User attempts to update processing status without internal secret -> 401 Unauthorized
        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED")
                        .header("X-User-Id", userA.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        // 3b. Authenticated user with assertion attempts to update processing status without internal secret -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED")
                        .header("X-Gateway-Assertion", createAssertion(userA, "ROLE_USER", "PATCH", "/api/v1/documents/" + docId + "/processing-status/COMPLETED"))
                        .header("X-User-Id", userA.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // 4. Internal pipeline updates processing status with valid secret -> 200 OK
        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED")
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("COMPLETED"));

        // 5. Verify status is persisted in the database
        Document updated = documentRepository.findById(docId).orElseThrow();
        assertEquals(Document.ProcessingStatus.COMPLETED, updated.getProcessingStatus());
    }
}
