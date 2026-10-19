CREATE TABLE document_metadata
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id         UUID NOT NULL,

    metadata_key        VARCHAR(100) NOT NULL,

    metadata_value      TEXT,

    value_type          VARCHAR(20) NOT NULL DEFAULT 'STRING'
                            CHECK (
                                value_type IN
                                (
                                    'STRING',
                                    'NUMBER',
                                    'BOOLEAN',
                                    'DATE',
                                    'JSON'
                                )
                            ),

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by          UUID,

    CONSTRAINT fk_metadata_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_metadata_document
ON document_metadata(document_id);

CREATE INDEX idx_metadata_key
ON document_metadata(metadata_key);

CREATE INDEX idx_metadata_value
ON document_metadata(metadata_value);

CREATE UNIQUE INDEX uq_document_metadata
ON document_metadata(document_id, metadata_key);