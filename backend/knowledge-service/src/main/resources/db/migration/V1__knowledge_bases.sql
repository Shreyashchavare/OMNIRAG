CREATE TABLE knowledge_bases
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name                VARCHAR(150) NOT NULL,
    description         TEXT,

    slug                VARCHAR(200) UNIQUE NOT NULL,

    owner_id            UUID NOT NULL,

    visibility          VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'
                            CHECK (visibility IN ('PRIVATE','PUBLIC','SHARED')),

    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','ARCHIVED','DELETED')),

    total_documents     INTEGER NOT NULL DEFAULT 0,

    total_size_bytes    BIGINT NOT NULL DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by          UUID,
    updated_by          UUID,

    deleted_at          TIMESTAMP,

    version             INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_kb_owner
ON knowledge_bases(owner_id);

CREATE INDEX idx_kb_status
ON knowledge_bases(status);

CREATE INDEX idx_kb_visibility
ON knowledge_bases(visibility);

CREATE INDEX idx_kb_name
ON knowledge_bases(name);