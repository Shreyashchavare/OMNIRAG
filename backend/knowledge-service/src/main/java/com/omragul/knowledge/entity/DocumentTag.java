package com.omragul.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "document_tags",
        indexes = {
                @Index(name = "idx_document_tag_document", columnList = "document_id"),
                @Index(name = "idx_document_tag_tag", columnList = "tag_id")
        }
)
@IdClass(DocumentTag.DocumentTagId.class)
public class DocumentTag {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    @Column(name = "tagged_by")
    private UUID taggedBy;

    @Column(name = "tagged_at", nullable = false)
    private LocalDateTime taggedAt;

    public DocumentTag() {
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public void setTagId(UUID tagId) {
        this.tagId = tagId;
    }

    public UUID getTaggedBy() {
        return taggedBy;
    }

    public void setTaggedBy(UUID taggedBy) {
        this.taggedBy = taggedBy;
    }

    public LocalDateTime getTaggedAt() {
        return taggedAt;
    }

    public void setTaggedAt(LocalDateTime taggedAt) {
        this.taggedAt = taggedAt;
    }

    public static class DocumentTagId implements Serializable {

        private UUID documentId;
        private UUID tagId;

        public DocumentTagId() {
        }

        public DocumentTagId(UUID documentId, UUID tagId) {
            this.documentId = documentId;
            this.tagId = tagId;
        }

        public UUID getDocumentId() {
            return documentId;
        }

        public void setDocumentId(UUID documentId) {
            this.documentId = documentId;
        }

        public UUID getTagId() {
            return tagId;
        }

        public void setTagId(UUID tagId) {
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof DocumentTagId other)) {
                return false;
            }

            return documentId != null
                    && documentId.equals(other.documentId)
                    && tagId != null
                    && tagId.equals(other.tagId);
        }

        @Override
        public int hashCode() {
            int result = documentId != null ? documentId.hashCode() : 0;
            result = 31 * result + (tagId != null ? tagId.hashCode() : 0);
            return result;
        }
    }
}