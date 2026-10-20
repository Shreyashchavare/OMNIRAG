package com.omragul.processing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.dto.request.ProcessingJobCreateRequest;
import com.omragul.processing.dto.response.DocumentProcessingStatusResponse;
import com.omragul.processing.dto.response.JobStatusResponse;
import com.omragul.processing.dto.response.ProcessingJobResponse;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import com.omragul.processing.exception.DuplicateJobException;
import com.omragul.processing.exception.GlobalExceptionHandler;
import com.omragul.processing.exception.IllegalStateTransitionException;
import com.omragul.processing.exception.JobNotFoundException;
import com.omragul.processing.service.ProcessingJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProcessingJobController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class ProcessingJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

    @MockitoBean
    private ProcessingJobService processingJobService;

    @Test
    @DisplayName("POST /api/v1/processing/jobs - Success 201 Created")
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

        mockMvc.perform(get("/api/v1/processing/jobs/{id}", jobId))
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

        mockMvc.perform(get("/api/v1/processing/jobs/{id}", jobId))
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

        mockMvc.perform(get("/api/v1/processing/jobs/{id}/status", jobId))
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

        mockMvc.perform(get("/api/v1/processing/documents/{documentId}/status", docId))
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

        mockMvc.perform(post("/api/v1/processing/jobs/{id}/retry", jobId))
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

        mockMvc.perform(post("/api/v1/processing/jobs/{id}/cancel", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
