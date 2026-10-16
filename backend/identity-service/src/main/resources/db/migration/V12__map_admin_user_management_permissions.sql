-- ============================================================================
-- OMNIRAG Identity Service
-- V12: Map User Management Permissions to ADMIN Role
-- ============================================================================

INSERT INTO role_permissions (
    role_permission_id,
    role_id,
    permission_id,
    granted_at
)
SELECT
    gen_random_uuid(),
    r.role_id,
    p.permission_id,
    NOW()
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'ADMIN'
  AND p.permission_name IN (
      'USER_LOCK',
      'USER_UNLOCK',
      'USER_RESTORE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;