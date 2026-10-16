CREATE TABLE documents
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    knowledge_base_id       UUID NOT NULL,

    folder_id               UUID,

    category_id             UUID,

    name                    VARCHAR(255) NOT NULL,

    original_filename       VARCHAR(500) NOT NULL,

    stored_filename         VARCHAR(500) NOT NULL,

    storage_path            TEXT NOT NULL,

    file_extension          VARCHAR(20),

    mime_type               VARCHAR(100) NOT NULL,

    file_size_bytes         BIGINT NOT NULL,

    checksum                VARCHAR(128) NOT NULL,

    owner_id                UUID NOT NULL,

    current_version         INTEGER NOT NULL DEFAULT 1,

    processing_status       VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                                CHECK (
                                    processing_status IN
                                    (
                                        'PENDING',
                                        'PROCESSING',
                                        'COMPLETED',
                                        'FAILED'
                                    )
                                ),

    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                CHECK (
                                    status IN
                                    (
                                        'ACTIVE',
                                        'ARCHIVED',
                                        'DELETED'
                                    )
                                ),

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by              UUID,
    updated_by              UUID,

    deleted_at              TIMESTAMP,

    version                 INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT fk_document_kb
        FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_document_folder
        FOREIGN KEY (folder_id)
        REFERENCES folders(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_document_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_document_kb
ON documents(knowledge_base_id);

CREATE INDEX idx_document_folder
ON documents(folder_id);

CREATE INDEX idx_document_category
ON documents(category_id);

CREATE INDEX idx_document_owner
ON documents(owner_id);

CREATE INDEX idx_document_status
ON documents(status);

CREATE INDEX idx_document_processing
ON documents(processing_status);

CREATE INDEX idx_document_name
ON documents(name);

CREATE INDEX idx_document_checksum
ON documents(checksum);