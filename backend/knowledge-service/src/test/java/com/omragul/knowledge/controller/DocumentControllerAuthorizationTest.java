package com.omragul.knowledge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.knowledge.dto.DocumentRequest;
import com.omragul.knowledge.dto.DocumentUpdateRequest;
import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.exception.GlobalExceptionHandler;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.exception.UnauthorizedException;
import com.omragul.knowledge.security.GatewayAssertionVerifier;
import com.omragul.knowledge.security.InternalAuthService;
import com.omragul.knowledge.security.SecurityAuditLogger;
import com.omragul.knowledge.service.DocumentService;
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

@WebMvcTest(DocumentController.class)
@Import({ GlobalExceptionHandler.class, GatewayAssertionVerifier.class, SecurityAuditLogger.class })
class DocumentControllerAuthorizationTest {

    private static final String TEST_SECRET = "test-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private InternalAuthService internalAuthService;

    @MockitoBean
    private com.omragul.knowledge.service.DocumentStorageService documentStorageService;

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
    @DisplayName("POST /api/v1/documents - Missing credentials returns 401 Unauthorized")
    void testCreateDocument_MissingCredentials_Returns401() throws Exception {
        DocumentRequest request = new DocumentRequest();
        request.setKnowledgeBaseId(UUID.randomUUID());
        request.setName("Doc 1");
        request.setOriginalFilename("doc1.pdf");
        request.setFileSizeBytes(1024L);

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required: missing user identity or internal service credentials"));
    }

    @Test
    @DisplayName("POST /api/v1/documents - Direct untrusted X-User-Id without Gateway assertion is rejected with 401")
    void testCreateDocument_DirectUntrustedUserId_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        DocumentRequest request = new DocumentRequest();
        request.setKnowledgeBaseId(UUID.randomUUID());
        request.setName("Doc 1");
        request.setOriginalFilename("doc1.pdf");
        request.setFileSizeBytes(1024L);

        mockMvc.perform(post("/api/v1/documents")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Direct client identity headers rejected: gateway assertion required"));
    }

    @Test
    @DisplayName("POST /api/v1/documents - Mismatched assertion and X-User-Id header (spoofing attempt) returns 401")
    void testCreateDocument_MismatchedAssertionUserId_Returns401() throws Exception {
        UUID realUser = UUID.randomUUID();
        UUID spoofedUser = UUID.randomUUID();
        DocumentRequest request = new DocumentRequest();
        request.setKnowledgeBaseId(UUID.randomUUID());
        request.setName("Doc 1");
        request.setOriginalFilename("doc1.pdf");
        request.setFileSizeBytes(1024L);

        mockMvc.perform(post("/api/v1/documents")
                        .header("X-Gateway-Assertion", createAssertion(realUser.toString(), "ROLE_USER", null, TEST_SECRET, "POST", "/api/v1/documents"))
                        .header("X-User-Id", spoofedUser.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Gateway assertion user ID does not match X-User-Id header"));
    }

    @Test
    @DisplayName("POST /api/v1/documents - With valid Gateway assertion returns 200 OK")
    void testCreateDocument_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        DocumentRequest request = new DocumentRequest();
        request.setKnowledgeBaseId(kbId);
        request.setName("Doc 1");
        request.setOriginalFilename("doc1.pdf");
        request.setFileSizeBytes(1024L);

        Document createdDoc = new Document();
        createdDoc.setId(docId);
        createdDoc.setKnowledgeBaseId(kbId);
        createdDoc.setOwnerId(userId);
        createdDoc.setName("Doc 1");
        createdDoc.setStatus(Document.Status.ACTIVE);
        createdDoc.setProcessingStatus(Document.ProcessingStatus.PENDING);

        when(documentService.create(any(Document.class), eq(userId))).thenReturn(createdDoc);

        mockMvc.perform(post("/api/v1/documents")
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "POST", "/api/v1/documents"))
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.ownerId").value(userId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} - Document not found returns 404")
    void testGetById_NotFound_Returns404() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(documentService.getById(docId, userId, false))
                .thenThrow(new ResourceNotFoundException("Document not found: " + docId));

        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/api/v1/documents/" + docId))
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} - Unauthorized access returns 403 Forbidden")
    void testGetById_Unauthorized_Returns403() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(documentService.getById(docId, userId, false))
                .thenThrow(new UnauthorizedException("Access denied to document"));

        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/api/v1/documents/" + docId))
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} - Allows internal caller with valid X-Internal-Secret")
    void testGetById_WithInternalSecret_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setName("Internal Doc");
        doc.setStatus(Document.Status.ACTIVE);

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.getById(docId, null, true)).thenReturn(doc);

        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} - Missing both X-User-Id and X-Internal-Secret returns 401 Unauthorized")
    void testGetById_MissingCredentials_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/{id}", docId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/documents/{id}/processing-status/{status} - Allows internal service with valid secret")
    void testUpdateProcessingStatus_WithInternalSecret_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setProcessingStatus(Document.ProcessingStatus.COMPLETED);

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.updateProcessingStatus(docId, Document.ProcessingStatus.COMPLETED)).thenReturn(doc);

        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED")
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/documents/{id}/processing-status/{status} - Allows internal service with X-Internal-Service-Secret alias")
    void testUpdateProcessingStatus_WithInternalServiceSecretAlias_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setProcessingStatus(Document.ProcessingStatus.COMPLETED);

        when(internalAuthService.isAuthorizedInternalService("valid-service-secret")).thenReturn(true);
        when(documentService.updateProcessingStatus(docId, Document.ProcessingStatus.COMPLETED)).thenReturn(doc);

        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED")
                        .header("X-Internal-Service-Secret", "valid-service-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/documents/{id}/processing-status/{status} - Missing internal secret returns 401 Unauthorized")
    void testUpdateProcessingStatus_MissingSecret_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PUT /api/v1/documents/{id} - Missing credentials returns 401 Unauthorized")
    void testUpdateDocument_MissingCredentials_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest();
        updateRequest.setName("Updated Title");

        mockMvc.perform(put("/api/v1/documents/{id}", docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PUT /api/v1/documents/{id} - Unauthorized update returns 403 Forbidden")
    void testUpdateDocument_Unauthorized_Returns403() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();

        when(documentService.update(eq(docId), any(Document.class), eq(attackerId)))
                .thenThrow(new UnauthorizedException("User does not have permission to update document"));

        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest();
        updateRequest.setName("Malicious Update");

        mockMvc.perform(put("/api/v1/documents/{id}", docId)
                        .header("X-Gateway-Assertion", createAssertion(attackerId.toString(), "ROLE_USER", null, TEST_SECRET, "PUT", "/api/v1/documents/" + docId))
                        .header("X-User-Id", attackerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("DELETE /api/v1/documents/{id} - Missing credentials returns 401 Unauthorized")
    void testDeleteDocument_MissingCredentials_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/documents/{id}", docId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/documents/{id} - Unauthorized delete returns 403 Forbidden")
    void testDeleteDocument_Unauthorized_Returns403() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();

        when(documentService.softDelete(docId, attackerId))
                .thenThrow(new UnauthorizedException("User does not have permission to delete document"));

        mockMvc.perform(delete("/api/v1/documents/{id}", docId)
                        .header("X-Gateway-Assertion", createAssertion(attackerId.toString(), "ROLE_USER", null, TEST_SECRET, "DELETE", "/api/v1/documents/" + docId))
                        .header("X-User-Id", attackerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/folder/{folderId} - Missing credentials returns 401 Unauthorized")
    void testGetByFolder_MissingCredentials_Returns401() throws Exception {
        UUID folderId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/folder/{folderId}", folderId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/category/{categoryId} - Missing credentials returns 401 Unauthorized")
    void testGetByCategory_MissingCredentials_Returns401() throws Exception {
        UUID categoryId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/category/{categoryId}", categoryId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/owner/{ownerId} - Missing credentials returns 401 Unauthorized")
    void testGetByOwner_MissingCredentials_Returns401() throws Exception {
        UUID ownerId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/owner/{ownerId}", ownerId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/documents/{id}/archive - Missing credentials returns 401 Unauthorized")
    void testArchiveDocument_MissingCredentials_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/documents/{id}/archive", docId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/documents/{id}/restore - Missing credentials returns 401 Unauthorized")
    void testRestoreDocument_MissingCredentials_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/documents/{id}/restore", docId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/permissions/verify - Missing or invalid internal secret returns 401 Unauthorized")
    void testVerifyPermission_UnauthorizedInternalSecret() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService(any())).thenReturn(false);

        mockMvc.perform(get("/api/v1/documents/{id}/permissions/verify", docId)
                        .param("userId", userId.toString())
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/permissions/verify - Non-existent document returns 404 Not Found")
    void testVerifyPermission_DocumentNotFound() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.existsById(docId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/documents/{id}/permissions/verify", docId)
                        .param("userId", userId.toString())
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/permissions/verify - Authorized user returns 200 with authorized: true")
    void testVerifyPermission_AuthorizedUser() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.existsById(docId)).thenReturn(true);
        when(documentService.hasDocumentWriteAccess(docId, userId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/documents/{id}/permissions/verify", docId)
                        .param("userId", userId.toString())
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.permission").value("WRITE"))
                .andExpect(jsonPath("$.authorized").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/permissions/verify - Unauthorized user returns 200 with authorized: false")
    void testVerifyPermission_UnauthorizedUser() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.existsById(docId)).thenReturn(true);
        when(documentService.hasDocumentWriteAccess(docId, userId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/documents/{id}/permissions/verify", docId)
                        .param("userId", userId.toString())
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.authorized").value(false));
    }

    // =========================================================
    // GET /api/v1/documents/{id}/content - STREAMING TESTS (PROC-04)
    // =========================================================

    @Test
    @DisplayName("GET /api/v1/documents/{id}/content - Missing credentials returns 401 Unauthorized")
    void testStreamContent_MissingCredentials_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/{id}/content", docId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/content - Invalid internal secret returns 401 Unauthorized")
    void testStreamContent_InvalidInternalSecret_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();
        when(internalAuthService.isAuthorizedInternalService("bad-secret")).thenReturn(false);

        mockMvc.perform(get("/api/v1/documents/{id}/content", docId)
                        .header("X-Internal-Secret", "bad-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/content - Authenticated internal service successfully streams content")
    void testStreamContent_InternalService_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setName("sample.pdf");
        doc.setOriginalFilename("original-sample.pdf");
        doc.setMimeType("application/pdf");
        doc.setStoragePath("sample.pdf");

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.getById(docId, null, true)).thenReturn(doc);
        when(documentStorageService.getFileSize(doc)).thenReturn(24L);
        when(documentStorageService.openStream(doc)).thenReturn(new java.io.ByteArrayInputStream("Mock PDF Stream Content".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/documents/{id}/content", docId)
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"original-sample.pdf\""))
                .andExpect(header().string("Content-Length", "24"))
                .andExpect(header().string("X-Document-Id", docId.toString()))
                .andExpect(content().string("Mock PDF Stream Content"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/content - Document not found in DB returns 404")
    void testStreamContent_DocumentNotFound_Returns404() throws Exception {
        UUID docId = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.getById(docId, null, true))
                .thenThrow(new ResourceNotFoundException("Document not found: " + docId));

        mockMvc.perform(get("/api/v1/documents/{id}/content", docId)
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/content - Physical storage file not found returns 404 without leaking path")
    void testStreamContent_PhysicalFileMissing_Returns404() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setName("missing.pdf");
        doc.setStoragePath("missing.pdf");

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.getById(docId, null, true)).thenReturn(doc);
        when(documentStorageService.getFileSize(doc))
                .thenThrow(new ResourceNotFoundException("Document storage file not found for document: " + docId));

        mockMvc.perform(get("/api/v1/documents/{id}/content", docId)
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Document storage file not found for document: " + docId));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/content - User with read access and valid assertion can stream content")
    void testStreamContent_UserAuthorized_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setName("notes.txt");
        doc.setOriginalFilename("notes.txt");
        doc.setMimeType("text/plain");
        doc.setStoragePath("notes.txt");
        doc.setOwnerId(userId);

        when(internalAuthService.isAuthorizedInternalService(any())).thenReturn(false);
        when(documentService.getById(docId, userId, false)).thenReturn(doc);
        when(documentStorageService.getFileSize(doc)).thenReturn(12L);
        when(documentStorageService.openStream(doc)).thenReturn(new java.io.ByteArrayInputStream("Hello World!".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/documents/{id}/content", docId)
                        .header("X-Gateway-Assertion", createAssertion(userId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/api/v1/documents/" + docId + "/content"))
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(content().string("Hello World!"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/content - Unauthorized user is rejected with 403 Forbidden")
    void testStreamContent_UserUnauthorized_Returns403() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService(any())).thenReturn(false);
        when(documentService.getById(docId, attackerId, false))
                .thenThrow(new UnauthorizedException("User does not have permission to view document: " + docId));

        mockMvc.perform(get("/api/v1/documents/{id}/content", docId)
                        .header("X-Gateway-Assertion", createAssertion(attackerId.toString(), "ROLE_USER", null, TEST_SECRET, "GET", "/api/v1/documents/" + docId + "/content"))
                        .header("X-User-Id", attackerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/content - Path traversal attempt is rejected with 403 Forbidden")
    void testStreamContent_PathTraversal_Returns403() throws Exception {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setStoragePath("../../etc/shadow");

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.getById(docId, null, true)).thenReturn(doc);
        when(documentStorageService.getFileSize(doc))
                .thenThrow(new SecurityException("Illegal path traversal attempt in storage path"));

        mockMvc.perform(get("/api/v1/documents/{id}/content", docId)
                        .header("X-Internal-Secret", "valid-secret"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access to requested resource or storage path is forbidden"));
    }

    @Test
    @DisplayName("POST /api/v1/documents/batch/status - Authorized internal service receives active document IDs")
    void testBatchStatus_AuthorizedInternalService_ReturnsActiveIds() throws Exception {
        UUID kbId = UUID.randomUUID();
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);
        when(documentService.getActiveDocumentIds(eq(kbId), any())).thenReturn(java.util.List.of(doc1));

        com.omragul.knowledge.dto.DocumentBatchStatusRequest request =
                new com.omragul.knowledge.dto.DocumentBatchStatusRequest(kbId, java.util.List.of(doc1, doc2));

        mockMvc.perform(post("/api/v1/documents/batch/status")
                        .header("X-Internal-Service-Secret", "valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgeBaseId").value(kbId.toString()))
                .andExpect(jsonPath("$.activeDocumentIds[0]").value(doc1.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/documents/batch/status - Unauthorized internal credentials rejected with 401 Unauthorized")
    void testBatchStatus_UnauthorizedCredentials_Returns401() throws Exception {
        UUID kbId = UUID.randomUUID();
        UUID doc1 = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService(any())).thenReturn(false);

        com.omragul.knowledge.dto.DocumentBatchStatusRequest request =
                new com.omragul.knowledge.dto.DocumentBatchStatusRequest(kbId, java.util.List.of(doc1));

        mockMvc.perform(post("/api/v1/documents/batch/status")
                        .header("X-Internal-Service-Secret", "bad-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/v1/documents/batch/status - Batch size exceeding 1000 items is rejected with 400 Bad Request")
    void testBatchStatus_ExceedsMaxBatchSize_Returns400() throws Exception {
        UUID kbId = UUID.randomUUID();
        java.util.List<UUID> excessiveList = java.util.stream.IntStream.range(0, 1001)
                .mapToObj(i -> UUID.randomUUID())
                .toList();

        when(internalAuthService.isAuthorizedInternalService("valid-secret")).thenReturn(true);

        com.omragul.knowledge.dto.DocumentBatchStatusRequest request =
                new com.omragul.knowledge.dto.DocumentBatchStatusRequest(kbId, excessiveList);

        mockMvc.perform(post("/api/v1/documents/batch/status")
                        .header("X-Internal-Service-Secret", "valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
