package com.omragul.processing.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.omragul.processing.enums.ProcessingStage;
import com.omragul.processing.enums.ProcessingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingJobResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    @DisplayName("ProcessingJobResponse serializes both snake_case and camelCase identifiers for cross-service compatibility")
    void testDualSerializationCompatibility() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        ProcessingJobResponse response = ProcessingJobResponse.builder()
                .id(jobId)
                .documentId(docId)
                .knowledgeBaseId(kbId)
                .ownerId(ownerId)
                .status(ProcessingStatus.QUEUED)
                .stage(ProcessingStage.INITIALIZATION)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(response);

        // Verify snake_case fields (PROC API contract)
        assertThat(json).contains("\"document_id\":\"" + docId + "\"");
        assertThat(json).contains("\"knowledge_base_id\":\"" + kbId + "\"");
        assertThat(json).contains("\"owner_id\":\"" + ownerId + "\"");

        // Verify camelCase fields (Integration test and client contract)
        assertThat(json).contains("\"documentId\":\"" + docId + "\"");
        assertThat(json).contains("\"knowledgeBaseId\":\"" + kbId + "\"");
        assertThat(json).contains("\"ownerId\":\"" + ownerId + "\"");
    }
}
