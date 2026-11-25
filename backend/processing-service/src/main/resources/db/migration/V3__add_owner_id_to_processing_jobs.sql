-- ============================================================================
-- Migration: V3__add_owner_id_to_processing_jobs.sql
-- Purpose: Add owner_id to processing_jobs table for user ownership and IDOR protection (PROC-01)
-- Safety:
--   1. Adds nullable owner_id UUID column to support existing historical records
--      without fabricating fake owners.
--   2. Creates an index on owner_id for efficient user-scoped queries and listing.
-- ============================================================================

ALTER TABLE processing_jobs ADD COLUMN IF NOT EXISTS owner_id UUID;

CREATE INDEX IF NOT EXISTS idx_processing_jobs_owner_id ON processing_jobs (owner_id);
