-- ============================================================================
-- OMNIRAG Identity Service
-- V10: Add User Lock, Unlock and Restore Permissions
-- ============================================================================

-- ============================================================================
-- 1. Add new values to permission_type enum
-- ============================================================================

ALTER TYPE permission_type
    ADD VALUE IF NOT EXISTS 'USER_LOCK';

ALTER TYPE permission_type
    ADD VALUE IF NOT EXISTS 'USER_UNLOCK';

ALTER TYPE permission_type
    ADD VALUE IF NOT EXISTS 'USER_RESTORE';
