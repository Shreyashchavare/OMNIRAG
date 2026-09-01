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
                @Index(name = "idx_documents_folder_id", columnList = "folder_id"),
                @Index(name = "idx_documents_uploaded_by", columnList = "uploaded_by")
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
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "checksum", nullable = false, length = 128)
    private String checksum;

    // Add the exact enum mapping here after we align it with V2 migration.
    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "document_status"
    )
    private DocumentStatus status;

}