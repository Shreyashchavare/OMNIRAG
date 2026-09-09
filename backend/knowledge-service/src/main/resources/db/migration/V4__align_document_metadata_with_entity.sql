-- ============================================================
-- OMRAGUL - Knowledge Service
-- Database: knowledge_db
-- Migration: V4
-- Description: Align document_metadata with DocumentMetadata entity
-- ============================================================


-- ============================================================
-- REMOVE OLD KEY/VALUE METADATA STRUCTURE
-- ============================================================

ALTER TABLE document_metadata
DROP CONSTRAINT IF EXISTS uk_document_metadata_key;

ALTER TABLE document_metadata
DROP COLUMN IF EXISTS metadata_key;

ALTER TABLE document_metadata
DROP COLUMN IF EXISTS metadata_value;


-- ============================================================
-- DOCUMENT METADATA FIELDS
-- ============================================================

ALTER TABLE document_metadata
ADD COLUMN IF NOT EXISTS title VARCHAR(255);

ALTER TABLE document_metadata
ADD COLUMN IF NOT EXISTS author VARCHAR(255);

ALTER TABLE document_metadata
ADD COLUMN IF NOT EXISTS language VARCHAR(50);

ALTER TABLE document_metadata
ADD COLUMN IF NOT EXISTS keywords VARCHAR(1000);


-- ============================================================
-- ONE-TO-ONE DOCUMENT RELATION
-- ============================================================

ALTER TABLE document_metadata
ADD CONSTRAINT uk_document_metadata_document
UNIQUE (document_id);


-- ============================================================
-- COMMENTS
-- ============================================================

COMMENT ON TABLE document_metadata IS
    'Metadata associated with a document.';

COMMENT ON COLUMN document_metadata.title IS
    'Title extracted from the document.';

COMMENT ON COLUMN document_metadata.author IS
    'Author extracted from the document.';

COMMENT ON COLUMN document_metadata.language IS
    'Language detected for the document.';

COMMENT ON COLUMN document_metadata.keywords IS
    'Keywords associated with the document.';