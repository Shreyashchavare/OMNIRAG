-- ============================================================================
-- OMNIRAG Identity Service
-- V4: Fix roles column name
-- ============================================================================

-- V2 renamed role_name -> role_type.
-- The JPA Role entity currently expects role_name.
-- Restore the database column name to match the entity mapping.

ALTER TABLE roles
    RENAME COLUMN role_type TO role_name;


-- Rename the index back to match the column name.

ALTER INDEX IF EXISTS idx_roles_role_type
    RENAME TO idx_roles_role_name;


-- Rename the unique constraint back to match the column name.

ALTER TABLE roles
    RENAME CONSTRAINT roles_role_type_key
    TO roles_role_name_key;