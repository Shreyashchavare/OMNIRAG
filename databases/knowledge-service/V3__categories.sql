CREATE TABLE categories
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    knowledge_base_id   UUID NOT NULL,

    parent_category_id  UUID,

    name                VARCHAR(100) NOT NULL,

    description         TEXT,

    color               VARCHAR(20),

    icon                VARCHAR(100),

    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (
                                status IN
                                ('ACTIVE','ARCHIVED','DELETED')
                            ),

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by          UUID,
    updated_by          UUID,

    deleted_at          TIMESTAMP,

    version             INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT fk_category_kb
        FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_parent_category
        FOREIGN KEY (parent_category_id)
        REFERENCES categories(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_category_kb
ON categories(knowledge_base_id);

CREATE INDEX idx_category_parent
ON categories(parent_category_id);

CREATE INDEX idx_category_status
ON categories(status);

CREATE UNIQUE INDEX uq_category_name
ON categories(knowledge_base_id, name);