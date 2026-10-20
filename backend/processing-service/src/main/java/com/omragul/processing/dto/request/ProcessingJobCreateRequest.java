package com.omragul.processing.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to initiate document processing")
public class ProcessingJobCreateRequest {

    @NotNull(message = "document_id is required")
    @JsonProperty("document_id")
    @Schema(description = "UUID of the document in Knowledge Service", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID documentId;

    @JsonProperty("knowledge_base_id")
    @Schema(description = "Optional Knowledge Base UUID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private UUID knowledgeBaseId;

    @JsonProperty("idempotency_key")
    @Schema(description = "Optional client-supplied idempotency key to prevent duplicate processing", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String idempotencyKey;
}
