-- ============================================================
-- OMRAGUL - Knowledge Service
-- Database: knowledge_db
-- Migration: V2
-- Description: Convert document status to native PostgreSQL ENUM
-- ============================================================


-- ============================================================
-- DOCUMENT STATUS ENUM
-- ============================================================

CREATE TYPE document_status AS ENUM (
    'UPLOADING',
    'UPLOADED',
    'PROCESSING',
    'PROCESSED',
    'FAILED',
    'ARCHIVED'
);


-- ============================================================
-- REMOVE OLD VARCHAR CHECK CONSTRAINT
-- ============================================================

ALTER TABLE documents
DROP CONSTRAINT chk_documents_status;


-- ============================================================
-- CONVERT STATUS COLUMN FROM VARCHAR TO ENUM
-- ============================================================

ALTER TABLE documents
ALTER COLUMN status TYPE document_status
USING status::document_status;


-- ============================================================
-- COMMENTS
-- ============================================================

COMMENT ON TYPE document_status IS
    'Lifecycle status of a document in the Knowledge Service.';