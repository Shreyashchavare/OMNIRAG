-- ============================================================================
-- OMRAGUL Notification Service — Database Schema Migration
-- Database: PostgreSQL / notification_db
-- Migration Version: V1
-- Purpose: Create notifications table with indexes for recipient queries & idempotency
-- ============================================================================

-- ============================================================================
-- SECTION 1: ENUM TYPES
-- ============================================================================

CREATE TYPE notification_type AS ENUM (
    'DOCUMENT_PROCESSING',
    'SYSTEM',
    'SECURITY'
);

-- ============================================================================
-- SECTION 2: NOTIFICATIONS TABLE
-- ============================================================================

CREATE TABLE notifications (
    id                  UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id        UUID                    NOT NULL,
    type                notification_type       NOT NULL,
    title               VARCHAR(255)            NOT NULL,
    message             TEXT                    NOT NULL,
    metadata            JSONB,
    source_service      VARCHAR(64),
    source_event_type   VARCHAR(64),
    source_event_id     VARCHAR(255),
    is_read             BOOLEAN                 NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ
);

-- ============================================================================
-- SECTION 3: INDEXES
-- ============================================================================

-- Primary query index: notifications for a recipient sorted by creation time
CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);

-- Unread filter & count index
CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_id, is_read, created_at DESC);

-- Idempotent ingestion index (partial unique index when source fields are provided)
CREATE UNIQUE INDEX idx_notifications_source_idempotency
    ON notifications (source_service, source_event_id)
    WHERE source_service IS NOT NULL AND source_event_id IS NOT NULL;
