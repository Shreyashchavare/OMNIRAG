-- ==============================================================================
-- Migration V2: Harden current_step column length and enforce uniqueness on idempotency_key
-- Addresses PROC-09 (current_step overflow) and PROC-07 (idempotency concurrency safety)
-- ==============================================================================

-- 1. Expand current_step from VARCHAR(100) to VARCHAR(500)
-- Non-destructive, metadata-only update in PostgreSQL; allows descriptive step reporting
-- without crashing transactions when downstream errors are appended.
ALTER TABLE processing_jobs ALTER COLUMN current_step TYPE VARCHAR(500);

-- 2. Drop legacy non-unique index on idempotency_key
DROP INDEX IF EXISTS idx_processing_jobs_idempotency_key;

-- 3. Precondition Check: Detect duplicate non-null (document_id, idempotency_key) pairs
-- Halts migration with clear actionable error if historical duplicates exist.
DO $$
DECLARE
    v_dup_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_dup_count
    FROM (
        SELECT document_id, idempotency_key
        FROM processing_jobs
        WHERE idempotency_key IS NOT NULL
        GROUP BY document_id, idempotency_key
        HAVING COUNT(*) > 1
    ) duplicates;

    IF v_dup_count > 0 THEN
        RAISE EXCEPTION 'Flyway Migration V2 Precondition Failed: Found % duplicate (document_id, idempotency_key) pair(s) in processing_jobs. Resolve duplicates manually before creating unique index idx_processing_jobs_doc_idempotency_uniq.', v_dup_count;
    END IF;
END $$;

-- 4. Create unique index on (document_id, idempotency_key)
-- In SQL standard and PostgreSQL, NULL != NULL, so multiple NULL idempotency keys
-- are permitted per document, while non-null idempotency keys are strictly deduplicated.
CREATE UNIQUE INDEX IF NOT EXISTS idx_processing_jobs_doc_idempotency_uniq
    ON processing_jobs (document_id, idempotency_key);

-- 5. Re-create non-unique index on idempotency_key alone for efficient lookup queries
CREATE INDEX IF NOT EXISTS idx_processing_jobs_idempotency_key
    ON processing_jobs (idempotency_key);
