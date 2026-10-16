CREATE TABLE tags
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    knowledge_base_id   UUID NOT NULL,

    name                VARCHAR(100) NOT NULL,

    description         TEXT,

    color               VARCHAR(20),

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by          UUID,
    updated_by          UUID,

    CONSTRAINT fk_tag_kb
        FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_tag_kb
ON tags(knowledge_base_id);

CREATE INDEX idx_tag_name
ON tags(name);

CREATE UNIQUE INDEX uq_tag_name
ON tags(knowledge_base_id, name);