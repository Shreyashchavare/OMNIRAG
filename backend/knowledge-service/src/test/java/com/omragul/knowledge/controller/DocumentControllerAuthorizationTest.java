package com.omragul.knowledge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.knowledge.dto.DocumentRequest;
import com.omragul.knowledge.dto.DocumentUpdateRequest;
import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.exception.GlobalExceptionHandler;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.exception.UnauthorizedException;
import com.omragul.knowledge.security.InternalAuthService;
import com.omragul.knowledge.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
class DocumentControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private InternalAuthService internalAuthService;

    @Test
    @DisplayName("POST /api/v1/documents - Missing X-User-Id header returns 400 Bad Request")
    void testCreateDocument_MissingHeader_Returns400() throws Exception {
        DocumentRequest request = new DocumentRequest();
        request.setKnowledgeBaseId(UUID.randomUUID());
        request.setName("Doc 1");
        request.setOriginalFilename("doc1.pdf");
        request.setFileSizeBytes(1024L);

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Required header 'X-User-Id' is missing"));
    }

    @Test
    @DisplayName("POST /api/v1/documents - With X-User-Id returns 200 OK")
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
    @DisplayName("GET /api/v1/documents/{id} - Missing both X-User-Id and X-Internal-Secret returns 403 Forbidden")
    void testGetById_MissingCredentials_Returns403() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/{id}", docId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
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
    @DisplayName("PATCH /api/v1/documents/{id}/processing-status/{status} - Missing internal secret returns 403 Forbidden")
    void testUpdateProcessingStatus_MissingSecret_Returns403() throws Exception {
        UUID docId = UUID.randomUUID();

        when(internalAuthService.isAuthorizedInternalService(null)).thenReturn(false);

        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PUT /api/v1/documents/{id} - Missing X-User-Id returns 400 Bad Request")
    void testUpdateDocument_MissingUserId_Returns400() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest();
        updateRequest.setName("Updated Title");

        mockMvc.perform(put("/api/v1/documents/{id}", docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
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
                        .header("X-User-Id", attackerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("DELETE /api/v1/documents/{id} - Missing X-User-Id returns 400 Bad Request")
    void testDeleteDocument_MissingUserId_Returns400() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/documents/{id}", docId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("DELETE /api/v1/documents/{id} - Unauthorized delete returns 403 Forbidden")
    void testDeleteDocument_Unauthorized_Returns403() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();

        when(documentService.softDelete(docId, attackerId))
                .thenThrow(new UnauthorizedException("User does not have permission to delete document"));

        mockMvc.perform(delete("/api/v1/documents/{id}", docId)
                        .header("X-User-Id", attackerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/folder/{folderId} - Missing X-User-Id returns 400 Bad Request")
    void testGetByFolder_MissingUserId_Returns400() throws Exception {
        UUID folderId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/folder/{folderId}", folderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/category/{categoryId} - Missing X-User-Id returns 400 Bad Request")
    void testGetByCategory_MissingUserId_Returns400() throws Exception {
        UUID categoryId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/category/{categoryId}", categoryId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/owner/{ownerId} - Missing X-User-Id returns 400 Bad Request")
    void testGetByOwner_MissingUserId_Returns400() throws Exception {
        UUID ownerId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/owner/{ownerId}", ownerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/v1/documents/{id}/archive - Missing X-User-Id returns 400 Bad Request")
    void testArchiveDocument_MissingUserId_Returns400() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/documents/{id}/archive", docId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/v1/documents/{id}/restore - Missing X-User-Id returns 400 Bad Request")
    void testRestoreDocument_MissingUserId_Returns400() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/documents/{id}/restore", docId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
