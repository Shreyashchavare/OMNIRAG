package com.omragul.processing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.config.JacksonConfig;
import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.DocumentProcessingStatusResponse;
import com.omragul.processing.dto.response.JobStatusResponse;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.DuplicateJobException;
import com.omragul.processing.exception.GlobalExceptionHandler;
import com.omragul.processing.exception.JobNotFoundException;
import com.omragul.processing.security.GatewayAssertionVerifier;
import com.omragul.processing.security.InternalAuthService;
import com.omragul.processing.security.SecurityAuditLogger;
import com.omragul.processing.service.ProcessingJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProcessingJobController.class)
@Import({ GlobalExceptionHandler.class, InternalAuthService.class, JacksonConfig.class, GatewayAssertionVerifier.class,
        SecurityAuditLogger.class })
@ActiveProfiles("test")
class ProcessingJobControllerTest {

    private static final String DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String TEST_SECRET = "test-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

    @MockitoBean
    private ProcessingJobService processingJobService;

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

    private String validGatewayAssertion;

    @BeforeEach
    void setUp() throws Exception {
        gatewayAssertionVerifier.setPublicKey(TEST_KEY_PAIR.getPublic());
        validGatewayAssertion = createAssertion(DEFAULT_USER_ID, "ROLE_USER", null, TEST_SECRET, "POST", "/api/v1/processing/jobs");
    }

    private String createAssertion(String userId, String roles, String requestId, String secret, String method, String path) throws Exception {
        long now = Instant.now().getEpochSecond();
        return Jwts.builder()
                .header().type("JWT").and()
                .id(UUID.randomUUID().toString())
                .issuer("gateway-service")
                .audience().single("processing-service")
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
    @DisplayName("Security: Protected endpoint without identity returns 401 Unauthorized")
    void testMissingIdentityReturns401() throws Exception {
        UUID docId = UUID.randomUUID();
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .build();

        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message")
                        .value("Authentication required: missing user identity or internal service credentials"));
    }

    @Test
    @DisplayName("Security: Direct untrusted client X-User-Id without Gateway assertion is rejected (401)")
    void testDirectUntrustedUserIdRejected() throws Exception {
        UUID docId = UUID.randomUUID();
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .build();

        mockMvc.perform(post("/api/v1/processing/jobs")
                .header("X-User-Id", DEFAULT_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message")
                        .value("Direct client identity headers rejected: gateway assertion required"));
    }

    @Test
    @DisplayName("Security: Tampered or invalid Gateway assertion returns 401 Unauthorized")
    void testTamperedGatewayAssertionReturns401() throws Exception {
        UUID docId = UUID.randomUUID();
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .build();

        mockMvc.perform(post("/api/v1/processing/jobs")
                .header("X-Gateway-Assertion", "invalid.assertion.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid or expired gateway assertion"));
    }

    @Test
    @DisplayName("Security: Protected endpoint with invalid internal secret returns 401 Unauthorized")
    void testInvalidInternalSecretReturns401() throws Exception {
        UUID docId = UUID.randomUUID();
        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .build();

        mockMvc.perform(post("/api/v1/processing/jobs")
                .header("X-Internal-Secret", "wrong-secret-value")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid internal service credentials"));
    }

    @Test
    @DisplayName("POST /api/v1/processing/jobs - Success 201 Created with verified Gateway assertion")
    void testCreateJobSuccess() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .build();

        ProcessingJobResponse response = ProcessingJobResponse.builder()
                .id(jobId)
                .documentId(docId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(processingJobService.createJob(any(ProcessingJobCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/processing/jobs")
                .header("X-Gateway-Assertion", validGatewayAssertion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.document_id").value(docId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    @DisplayName("POST /api/v1/processing/jobs - Validation failure (missing document_id) 400 Bad Request")
    void testCreateJobValidationFailure() throws Exception {
        ProcessingJobCreateRequest request = new ProcessingJobCreateRequest(); // missing document_id

        mockMvc.perform(post("/api/v1/processing/jobs")
                .header("X-Gateway-Assertion", validGatewayAssertion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/processing/jobs - Duplicate active job returns 409 Conflict")
    void testCreateJobDuplicateConflict() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID existingJobId = UUID.randomUUID();

        ProcessingJobCreateRequest request = ProcessingJobCreateRequest.builder()
                .documentId(docId)
                .build();

        when(processingJobService.createJob(any()))
                .thenThrow(new DuplicateJobException("Document is already being processed", existingJobId));

        mockMvc.perform(post("/api/v1/processing/jobs")
                .header("X-Gateway-Assertion", validGatewayAssertion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_JOB"));
    }

    @Test
    @DisplayName("GET /api/v1/processing/jobs/{id} - Success 200 OK")
    void testGetJobByIdSuccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        ProcessingJobResponse response = ProcessingJobResponse.builder()
                .id(jobId)
                .documentId(docId)
                .status(ProcessingStatus.EXTRACTING)
                .stage(ProcessingStage.EXTRACTION)
                .attemptCount(1)
                .maxAttempts(3)
                .build();

        when(processingJobService.getJobById(jobId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/processing/jobs/{id}", jobId)
                .header("X-Gateway-Assertion", createAssertion(DEFAULT_USER_ID, "ROLE_USER", null, TEST_SECRET, "GET", "/api/v1/processing/jobs/" + jobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("EXTRACTING"))
                .andExpect(jsonPath("$.stage").value("EXTRACTION"));
    }

    @Test
    @DisplayName("GET /api/v1/processing/jobs/{id} - Not Found 404")
    void testGetJobByIdNotFound() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(processingJobService.getJobById(jobId)).thenThrow(new JobNotFoundException(jobId));

        mockMvc.perform(get("/api/v1/processing/jobs/{id}", jobId)
                .header("X-Gateway-Assertion", createAssertion(DEFAULT_USER_ID, "ROLE_USER", null, TEST_SECRET, "GET", "/api/v1/processing/jobs/" + jobId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/processing/jobs/{id}/status - Success 200 OK")
    void testGetJobStatusSuccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        JobStatusResponse statusResponse = JobStatusResponse.builder()
                .id(jobId)
                .documentId(docId)
                .status(ProcessingStatus.CHUNKING)
                .stage(ProcessingStage.CHUNKING)
                .currentStep("Splitting text")
                .build();

        when(processingJobService.getJobStatus(jobId)).thenReturn(statusResponse);

        mockMvc.perform(get("/api/v1/processing/jobs/{id}/status", jobId)
                .header("X-Gateway-Assertion", createAssertion(DEFAULT_USER_ID, "ROLE_USER", null, TEST_SECRET, "GET", "/api/v1/processing/jobs/" + jobId + "/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHUNKING"))
                .andExpect(jsonPath("$.current_step").value("Splitting text"));
    }

    @Test
    @DisplayName("GET /api/v1/processing/documents/{documentId}/status - Success 200 OK")
    void testGetDocumentStatusSuccess() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        DocumentProcessingStatusResponse response = DocumentProcessingStatusResponse.builder()
                .documentId(docId)
                .latestJobId(jobId)
                .status(ProcessingStatus.COMPLETED)
                .stage(ProcessingStage.COMPLETED)
                .build();

        when(processingJobService.getDocumentStatus(docId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/processing/documents/{documentId}/status", docId)
                .header("X-Gateway-Assertion", createAssertion(DEFAULT_USER_ID, "ROLE_USER", null, TEST_SECRET, "GET", "/api/v1/processing/documents/" + docId + "/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_id").value(docId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/v1/processing/jobs/{id}/retry - Success 200 OK")
    void testRetryJobSuccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProcessingJobResponse response = ProcessingJobResponse.builder()
                .id(jobId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .build();

        when(processingJobService.retryJob(jobId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/processing/jobs/{id}/retry", jobId)
                .header("X-Gateway-Assertion", createAssertion(DEFAULT_USER_ID, "ROLE_USER", null, TEST_SECRET, "POST", "/api/v1/processing/jobs/" + jobId + "/retry")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    @DisplayName("POST /api/v1/processing/jobs/{id}/cancel - Success 200 OK")
    void testCancelJobSuccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProcessingJobResponse response = ProcessingJobResponse.builder()
                .id(jobId)
                .status(ProcessingStatus.CANCELLED)
                .stage(ProcessingStage.CANCELLED)
                .build();

        when(processingJobService.cancelJob(jobId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/processing/jobs/{id}/cancel", jobId)
                .header("X-Gateway-Assertion", createAssertion(DEFAULT_USER_ID, "ROLE_USER", null, TEST_SECRET, "POST", "/api/v1/processing/jobs/" + jobId + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
