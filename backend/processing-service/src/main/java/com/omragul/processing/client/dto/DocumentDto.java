package com.omragul.processing.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentDto {

    private UUID id;
    private UUID knowledgeBaseId;
    private UUID folderId;
    private UUID categoryId;
    private String name;
    private String originalFilename;
    private String storedFilename;
    private String storagePath;
    private String fileExtension;
    private String mimeType;
    private Long fileSizeBytes;
    private String checksum;
    private UUID ownerId;
    private Integer currentVersion;
    private String processingStatus;
    private String status;
}
