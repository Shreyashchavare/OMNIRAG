-- ============================================================================
-- OMRAGUL Analytics Service — Database Schema Migration
-- Database: PostgreSQL / analytics_db
-- Migration Version: V1
-- Purpose: Create analytics_events table for event-driven analytics storage
-- ============================================================================

-- ============================================================================
-- SECTION 1: ENUM TYPES
-- ============================================================================

CREATE TYPE event_type AS ENUM (
    'DOCUMENT_UPLOADED',
    'DOCUMENT_PROCESSED',
    'DOCUMENT_FAILED',
    'DOCUMENT_DELETED',
    'QUERY_EXECUTED',
    'QUERY_SUCCEEDED',
    'QUERY_FAILED',
    'MODEL_USAGE',
    'USER_LOGIN',
    'USER_LOGOUT',
    'USER_REGISTERED'
);

CREATE TYPE event_status AS ENUM (
    'SUCCESS',
    'FAILURE',
    'PENDING',
    'PARTIAL'
);

CREATE TYPE source_service AS ENUM (
    'IDENTITY_SERVICE',
    'KNOWLEDGE_SERVICE',
    'PROCESSING_SERVICE',
    'RAG_SERVICE',
    'AGENT_SERVICE',
    'MULTIMODAL_SERVICE',
    'EMBEDDING_SERVICE'
);

-- ============================================================================
-- SECTION 2: ANALYTICS EVENTS TABLE
-- ============================================================================

CREATE TABLE analytics_events (
    id                  BIGSERIAL               NOT NULL,
    event_id            UUID                    NOT NULL,
    event_type          event_type              NOT NULL,
    source_service      source_service          NOT NULL,
    occurred_at         TIMESTAMPTZ             NOT NULL,
    received_at         TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    correlation_id      UUID,
    actor_id            UUID,
    status              event_status            NOT NULL,
    duration_ms         BIGINT                  CHECK (duration_ms IS NULL OR duration_ms >= 0),
    input_tokens        INTEGER                 CHECK (input_tokens IS NULL OR input_tokens >= 0),
    output_tokens       INTEGER                 CHECK (output_tokens IS NULL OR output_tokens >= 0),
    model_name          VARCHAR(128),
    resource_id         VARCHAR(256),

    CONSTRAINT analytics_events_pkey PRIMARY KEY (id),
    CONSTRAINT analytics_events_event_id_unique UNIQUE (event_id)
);

COMMENT ON TABLE analytics_events IS 'Immutable append-only analytics event log; each row is one business event received from a producer service.';
COMMENT ON COLUMN analytics_events.event_id IS 'Producer-generated idempotency key; guaranteed unique per event.';
COMMENT ON COLUMN analytics_events.actor_id IS 'Opaque identifier of the actor (e.g. user UUID) — stored only as an opaque reference, not a foreign key.';
COMMENT ON COLUMN analytics_events.resource_id IS 'Opaque identifier of the resource involved (e.g. document ID, query ID).';

-- ============================================================================
-- SECTION 3: INDEXES
-- ============================================================================

-- Primary filter patterns: event type + time, source service + time, time only
CREATE INDEX idx_analytics_events_type_occurred
    ON analytics_events (event_type, occurred_at DESC);

CREATE INDEX idx_analytics_events_source_occurred
    ON analytics_events (source_service, occurred_at DESC);

CREATE INDEX idx_analytics_events_occurred
    ON analytics_events (occurred_at DESC);

CREATE INDEX idx_analytics_events_status_occurred
    ON analytics_events (status, occurred_at DESC);

CREATE INDEX idx_analytics_events_actor_id
    ON analytics_events (actor_id)
    WHERE actor_id IS NOT NULL;
