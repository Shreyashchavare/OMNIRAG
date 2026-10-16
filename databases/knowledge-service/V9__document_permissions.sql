CREATE TABLE document_permissions
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id         UUID NOT NULL,

    principal_type      VARCHAR(20) NOT NULL
                            CHECK (
                                principal_type IN
                                ('USER','ROLE')
                            ),

    principal_id        UUID NOT NULL,

    permission          VARCHAR(20) NOT NULL
                            CHECK (
                                permission IN
                                (
                                    'READ',
                                    'WRITE',
                                    'DELETE',
                                    'DOWNLOAD',
                                    'SHARE',
                                    'OWNER'
                                )
                            ),

    granted_by          UUID,

    granted_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    expires_at          TIMESTAMP,

    CONSTRAINT fk_document_permission
        FOREIGN KEY(document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_doc_permission_document
ON document_permissions(document_id);

CREATE INDEX idx_doc_permission_principal
ON document_permissions(principal_id);

CREATE UNIQUE INDEX uq_doc_permission
ON document_permissions
(
    document_id,
    principal_type,
    principal_id,
    permission
);