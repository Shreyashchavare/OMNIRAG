CREATE TABLE folders
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    knowledge_base_id   UUID NOT NULL,

    parent_folder_id    UUID,

    name                VARCHAR(255) NOT NULL,

    path                TEXT NOT NULL,

    level               INTEGER NOT NULL DEFAULT 0,

    owner_id            UUID NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),

    total_documents     INTEGER NOT NULL DEFAULT 0,

    total_subfolders    INTEGER NOT NULL DEFAULT 0,

    total_size_bytes    BIGINT NOT NULL DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by          UUID,
    updated_by          UUID,

    deleted_at          TIMESTAMP,

    version             INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT fk_folder_knowledge_base
        FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_parent_folder
        FOREIGN KEY (parent_folder_id)
        REFERENCES folders(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_folder_kb
ON folders(knowledge_base_id);

CREATE INDEX idx_folder_parent
ON folders(parent_folder_id);

CREATE INDEX idx_folder_owner
ON folders(owner_id);

CREATE INDEX idx_folder_status
ON folders(status);

CREATE INDEX idx_folder_name
ON folders(name);

CREATE UNIQUE INDEX uq_folder_name
ON folders(knowledge_base_id, parent_folder_id, name);