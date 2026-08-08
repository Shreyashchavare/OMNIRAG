-- ============================================================================
-- OMNIRAG Identity Service
-- V3: Fix permissions column name
-- ============================================================================

-- V2 renamed permission_name -> permission_type.
-- The JPA Permission entity currently expects permission_name.
-- Restore the database column name to match the entity mapping.

ALTER TABLE permissions
    RENAME COLUMN permission_type TO permission_name;


-- Rename the index back to match the column name.

ALTER INDEX IF EXISTS idx_permissions_permission_type
    RENAME TO idx_permissions_permission_name;


-- Rename the unique constraint back to match the column name.

ALTER TABLE permissions
    RENAME CONSTRAINT permissions_permission_type_key
    TO permissions_permission_name_key;