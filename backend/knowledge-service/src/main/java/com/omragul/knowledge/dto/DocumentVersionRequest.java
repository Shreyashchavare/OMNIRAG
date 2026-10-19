package com.omragul.knowledge.dto;

import com.omragul.knowledge.entity.DocumentVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public class DocumentVersionRequest {

    @NotNull
    private UUID documentId;

    @NotNull
    @Positive
    private Integer versionNumber;

    @NotBlank
    private String originalFilename;

    @NotBlank
    private String storedFilename;

    @NotBlank
    private String storagePath;

    @NotNull
    private DocumentVersion.StorageProvider storageProvider;

    private String fileExtension;

    @NotBlank
    private String mimeType;

    @NotNull
    @Positive
    private Long fileSizeBytes;

    @NotBlank
    private String checksum;

    private String uploadComment;

    @NotNull
    private UUID uploadedBy;

    public DocumentVersionRequest() {
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public DocumentVersion.StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(
            DocumentVersion.StorageProvider storageProvider
    ) {
        this.storageProvider = storageProvider;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getUploadComment() {
        return uploadComment;
    }

    public void setUploadComment(String uploadComment) {
        this.uploadComment = uploadComment;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}