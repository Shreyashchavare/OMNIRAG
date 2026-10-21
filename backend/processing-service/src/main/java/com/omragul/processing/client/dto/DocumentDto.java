package com.omragul.processing.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonAlias({"knowledgeBaseId", "knowledge_base_id"})
    private UUID knowledgeBaseId;

    @JsonAlias({"folderId", "folder_id"})
    private UUID folderId;

    @JsonAlias({"categoryId", "category_id"})
    private UUID categoryId;

    private String name;

    @JsonAlias({"originalFilename", "original_filename"})
    private String originalFilename;

    @JsonAlias({"storedFilename", "stored_filename"})
    private String storedFilename;

    @JsonAlias({"storagePath", "storage_path"})
    private String storagePath;

    @JsonAlias({"fileExtension", "file_extension"})
    private String fileExtension;

    @JsonAlias({"mimeType", "mime_type"})
    private String mimeType;

    @JsonAlias({"fileSizeBytes", "file_size_bytes"})
    private Long fileSizeBytes;

    private String checksum;

    @JsonAlias({"ownerId", "owner_id"})
    private UUID ownerId;

    @JsonAlias({"currentVersion", "current_version"})
    private Integer currentVersion;

    @JsonAlias({"processingStatus", "processing_status"})
    private String processingStatus;

    private String status;
}
