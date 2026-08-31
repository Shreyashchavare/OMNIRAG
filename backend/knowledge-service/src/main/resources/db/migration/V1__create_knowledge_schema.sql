-- ============================================================
-- OMRAGUL - Knowledge Service
-- Database: knowledge_db
-- Migration: V1
-- Description: Create Knowledge Management schema
-- ============================================================

-- ============================================================
-- UUID EXTENSION
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ============================================================
-- DEPARTMENTS
-- ============================================================

CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),

    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    deleted_by UUID,
    deleted_at TIMESTAMP,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_departments_name
        UNIQUE (name)
);


-- ============================================================
-- KNOWLEDGE BASES
-- ============================================================

CREATE TABLE knowledge_bases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    department_id UUID,

    -- UUID of the user owned by Identity Service.
    -- No cross-database foreign key is intentionally created.
    owner_id UUID NOT NULL,

    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000),

    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    deleted_by UUID,
    deleted_at TIMESTAMP,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_knowledge_bases_department
        FOREIGN KEY (department_id)
        REFERENCES departments(id)
);


-- ============================================================
-- FOLDERS
-- ============================================================

CREATE TABLE folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    knowledge_base_id UUID NOT NULL,

    -- Self-reference for nested folders.
    parent_folder_id UUID,

    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),

    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    deleted_by UUID,
    deleted_at TIMESTAMP,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_folders_knowledge_base
        FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases(id),

    CONSTRAINT fk_folders_parent
        FOREIGN KEY (parent_folder_id)
        REFERENCES folders(id)
);


-- ============================================================
-- DOCUMENTS
-- ============================================================

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    knowledge_base_id UUID NOT NULL,

    folder_id UUID,

    -- UUID of the user owned by Identity Service.
    -- No cross-database foreign key is intentionally created.
    owner_id UUID NOT NULL,

    original_filename VARCHAR(255) NOT NULL,

    stored_filename VARCHAR(255) NOT NULL,

    content_type VARCHAR(100) NOT NULL,

    file_size BIGINT NOT NULL,

    file_hash VARCHAR(128),

    storage_path VARCHAR(1000) NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    deleted_by UUID,
    deleted_at TIMESTAMP,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_documents_knowledge_base
        FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_bases(id),

    CONSTRAINT fk_documents_folder
        FOREIGN KEY (folder_id)
        REFERENCES folders(id),

    CONSTRAINT uk_documents_stored_filename
        UNIQUE (stored_filename),

    CONSTRAINT chk_documents_file_size
        CHECK (file_size >= 0),

    CONSTRAINT chk_documents_status
        CHECK (
            status IN (
                'UPLOADING',
                'UPLOADED',
                'PROCESSING',
                'PROCESSED',
                'FAILED',
                'ARCHIVED'
            )
        )
);


-- ============================================================
-- DOCUMENT METADATA
-- ============================================================

CREATE TABLE document_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id UUID NOT NULL,

    metadata_key VARCHAR(100) NOT NULL,

    metadata_value TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document_metadata_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_document_metadata_key
        UNIQUE (document_id, metadata_key)
);


-- ============================================================
-- INDEXES
-- ============================================================

-- Departments
CREATE INDEX idx_departments_name
    ON departments(name);


-- Knowledge Bases
CREATE INDEX idx_knowledge_bases_department_id
    ON knowledge_bases(department_id);

CREATE INDEX idx_knowledge_bases_owner_id
    ON knowledge_bases(owner_id);

CREATE INDEX idx_knowledge_bases_active
    ON knowledge_bases(id)
    WHERE is_deleted = FALSE;


-- Folders
CREATE INDEX idx_folders_knowledge_base_id
    ON folders(knowledge_base_id);

CREATE INDEX idx_folders_parent_folder_id
    ON folders(parent_folder_id);

CREATE INDEX idx_folders_active
    ON folders(knowledge_base_id)
    WHERE is_deleted = FALSE;


-- Documents
CREATE INDEX idx_documents_knowledge_base_id
    ON documents(knowledge_base_id);

CREATE INDEX idx_documents_folder_id
    ON documents(folder_id);

CREATE INDEX idx_documents_owner_id
    ON documents(owner_id);

CREATE INDEX idx_documents_status
    ON documents(status);

CREATE INDEX idx_documents_file_hash
    ON documents(file_hash);

CREATE INDEX idx_documents_active
    ON documents(knowledge_base_id)
    WHERE is_deleted = FALSE;


-- Document Metadata
CREATE INDEX idx_document_metadata_document_id
    ON document_metadata(document_id);


-- ============================================================
-- COMMENTS
-- ============================================================

COMMENT ON TABLE departments IS
    'Organizational departments that group knowledge bases.';

COMMENT ON TABLE knowledge_bases IS
    'Logical collections of related knowledge and documents.';

COMMENT ON TABLE folders IS
    'Folders used to organize documents within knowledge bases.';

COMMENT ON TABLE documents IS
    'Uploaded documents and their storage/lifecycle metadata.';

COMMENT ON TABLE document_metadata IS
    'Additional key-value metadata associated with documents.';