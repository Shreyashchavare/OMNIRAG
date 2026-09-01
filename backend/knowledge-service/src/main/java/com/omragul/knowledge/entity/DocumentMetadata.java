package com.omragul.knowledge.entity;

import com.omragul.knowledge.entity.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "document_metadata",
        indexes = {
                @Index(name = "idx_document_metadata_document_id", columnList = "document_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMetadata extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "document_id",
            nullable = false,
            unique = true
    )
    private Document document;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "author", length = 255)
    private String author;

    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "keywords", length = 1000)
    private String keywords;
}