package com.omragul.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentBatchStatusRequest {

    @NotNull(message = "Knowledge base ID is required")
    private UUID knowledgeBaseId;

    @NotNull(message = "Document IDs list is required")
    @Size(max = 1000, message = "Document IDs list cannot exceed 1000 items")
    private List<UUID> documentIds;
}
