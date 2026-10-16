CREATE TABLE document_versions
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id             UUID NOT NULL,

    version_number          INTEGER NOT NULL,

    original_filename       VARCHAR(500) NOT NULL,

    stored_filename         VARCHAR(500) NOT NULL,

    storage_path            TEXT NOT NULL,

    storage_provider        VARCHAR(20) NOT NULL DEFAULT 'LOCAL'
                                CHECK (
                                    storage_provider IN
                                    ('LOCAL','MINIO','S3')
                                ),

    file_extension          VARCHAR(20),

    mime_type               VARCHAR(100) NOT NULL,

    file_size_bytes         BIGINT NOT NULL,

    checksum                VARCHAR(128) NOT NULL,

    upload_comment          TEXT,

    uploaded_by             UUID NOT NULL,

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_version
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_doc_version_document
ON document_versions(document_id);

CREATE INDEX idx_doc_version_number
ON document_versions(document_id, version_number);

CREATE INDEX idx_doc_version_checksum
ON document_versions(checksum);

CREATE UNIQUE INDEX uq_document_version
ON document_versions(document_id, version_number);