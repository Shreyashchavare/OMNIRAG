package com.omragul.knowledge.entity;

import com.omragul.knowledge.entity.common.AuditableEntity;
import com.omragul.knowledge.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "documents",
        indexes = {
                @Index(name = "idx_documents_knowledge_base_id", columnList = "knowledge_base_id"),
                @Index(name = "idx_documents_folder_id", columnList = "folder_id"),
                @Index(name = "idx_documents_owner_id", columnList = "owner_id"),
                @Index(name = "idx_documents_file_hash", columnList = "file_hash"),
                @Index(name = "idx_documents_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "knowledge_base_id",
            nullable = false
    )
    private KnowledgeBase knowledgeBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "folder_id"
    )
    private Folder folder;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "document_status"
    )
    private DocumentStatus status;
}