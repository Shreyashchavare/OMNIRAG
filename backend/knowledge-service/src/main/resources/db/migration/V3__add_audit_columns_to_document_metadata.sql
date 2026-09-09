-- ============================================================
-- OMRAGUL - Knowledge Service
-- Database: knowledge_db
-- Migration: V3
-- Description: Add audit fields to document_metadata
-- ============================================================

ALTER TABLE document_metadata
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID,
    ADD COLUMN deleted_by UUID,
    ADD COLUMN deleted_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;