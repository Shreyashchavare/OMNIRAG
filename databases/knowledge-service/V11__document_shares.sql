CREATE TABLE document_shares
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id         UUID NOT NULL,

    shared_by           UUID NOT NULL,

    shared_with         UUID NOT NULL,

    access_level        VARCHAR(20) NOT NULL
                            CHECK (
                                access_level IN
                                (
                                    'VIEW',
                                    'COMMENT',
                                    'EDIT'
                                )
                            ),

    expires_at          TIMESTAMP,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_share
        FOREIGN KEY(document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_document_share_document
ON document_shares(document_id);

CREATE INDEX idx_document_share_user
ON document_shares(shared_with);