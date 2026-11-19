package com.omragul.knowledge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.knowledge.dto.DocumentRequest;
import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.entity.KnowledgeBase;
import com.omragul.knowledge.repository.DocumentRepository;
import com.omragul.knowledge.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID userA;
    private UUID userB;
    private KnowledgeBase privateKbA;

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("Integration: Controller -> Real Service -> DB User Isolation")
    void testRealService_UserIsolation() throws Exception {
        // 1. User A creates document
        DocumentRequest request = new DocumentRequest();
        request.setKnowledgeBaseId(privateKbA.getId());
        request.setName("TradeSecrets.pdf");
        request.setOriginalFilename("tradesecrets.pdf");
        request.setStoredFilename("stored_tradesecrets.pdf");
        request.setStoragePath("/data/docs/tradesecrets.pdf");
        request.setMimeType("application/pdf");
        request.setFileSizeBytes(4096L);
        request.setChecksum("sha256-abc1234567890");

        String responseContent = mockMvc.perform(post("/api/v1/documents")
                        .header("X-User-Id", userA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TradeSecrets.pdf"))
                .andExpect(jsonPath("$.ownerId").value(userA.toString()))
                .andReturn().getResponse().getContentAsString();

        UUID docId = UUID.fromString(objectMapper.readTree(responseContent).get("id").asText());

        // 2. User B tries to read User A's document -> 403 Forbidden
        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-User-Id", userB.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // 3. User A reads their own document -> 200 OK
        mockMvc.perform(get("/api/v1/documents/{id}", docId)
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
                        .header("X-Internal-Secret", "test-internal-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()));

        // 2. Caller with invalid internal secret -> 403 Forbidden
        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("X-Internal-Secret", "unauthorized-secret-attempt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // 3. User attempts to update processing status without internal secret -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED")
                        .header("X-User-Id", userA.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // 4. Internal pipeline updates processing status with valid secret -> 200 OK
        mockMvc.perform(patch("/api/v1/documents/{id}/processing-status/{status}", docId, "COMPLETED")
                        .header("X-Internal-Secret", "test-internal-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("COMPLETED"));

        // 5. Verify status is persisted in the database
        Document updated = documentRepository.findById(docId).orElseThrow();
        assertEquals(Document.ProcessingStatus.COMPLETED, updated.getProcessingStatus());
    }
}
