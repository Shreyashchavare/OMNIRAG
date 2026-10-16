CREATE TABLE document_tags
(
    document_id     UUID NOT NULL,

    tag_id          UUID NOT NULL,

    tagged_by       UUID,

    tagged_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY(document_id, tag_id),

    CONSTRAINT fk_document_tag_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_document_tag_tag
        FOREIGN KEY (tag_id)
        REFERENCES tags(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_document_tag_document
ON document_tags(document_id);

CREATE INDEX idx_document_tag_tag
ON document_tags(tag_id);