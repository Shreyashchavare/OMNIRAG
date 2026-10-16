-- ============================================================================
-- OMNIRAG Identity Service
-- V11: Insert User Lock, Unlock and Restore Permissions
-- ============================================================================

INSERT INTO permissions (
    permission_id,
    permission_name,
    description
)
VALUES
(
    gen_random_uuid(),
    'USER_LOCK',
    'Allows locking a user account'
),
(
    gen_random_uuid(),
    'USER_UNLOCK',
    'Allows unlocking a user account'
),
(
    gen_random_uuid(),
    'USER_RESTORE',
    'Allows restoring a deleted user account'
)
ON CONFLICT (permission_name) DO NOTHING;