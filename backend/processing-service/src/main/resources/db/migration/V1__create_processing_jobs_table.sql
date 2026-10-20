CREATE TABLE IF NOT EXISTS processing_jobs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    knowledge_base_id UUID,
    status VARCHAR(30) NOT NULL,
    stage VARCHAR(30) NOT NULL,
    current_step VARCHAR(100),
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    error_code VARCHAR(100),
    error_message TEXT,
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_processing_jobs_document_id ON processing_jobs (document_id);
CREATE INDEX IF NOT EXISTS idx_processing_jobs_status ON processing_jobs (status);
CREATE INDEX IF NOT EXISTS idx_processing_jobs_idempotency_key ON processing_jobs (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_processing_jobs_created_at ON processing_jobs (created_at);
