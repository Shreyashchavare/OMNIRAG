-- ============================================================================
-- OMNIRAG Identity Service - Database Schema Migration
-- Database: PostgreSQL
-- Migration Version: V2
-- Purpose:
--   1. Align PostgreSQL enum values with the final Java enums.
--   2. Align RBAC column names with Java property naming.
--   3. Align selected VARCHAR lengths with the entity definitions.
--
-- IMPORTANT:
--   V1 has already been applied. Do NOT modify V1__create_identity_schema.sql.
--   Run this file as:
--   V2__align_identity_schema.sql
--
-- Assumption:
--   The identity database is still in development and the roles/permissions
--   tables do not contain values that need data-preserving remapping.
--   The old ROLE_TYPE values MANAGER/VIEWER/EDITOR are intentionally replaced
--   by ADMIN/USER/CONTROLLER, as decided for the final Java enum.
-- ============================================================================


-- ============================================================================
-- SECTION 1: ALIGN LOGIN STATUS ENUM
-- ============================================================================

-- V1 already contains:
--   SUCCESS, FAILED, LOCKED, EXPIRED
-- Final Java enum additionally contains:
--   LOGOUT

ALTER TYPE login_status
    ADD VALUE IF NOT EXISTS 'LOGOUT';


-- ============================================================================
-- SECTION 2: ALIGN ROLE TYPE ENUM
-- ============================================================================
--
-- Final Java enum:
--
-- public enum RoleType {
--     ADMIN,
--     USER,
--     CONTROLLER
-- }
--
-- V1 contained:
--     ADMIN,
--     USER,
--     MANAGER,
--     VIEWER,
--     EDITOR
--
-- PostgreSQL enums cannot directly remove existing values, so a replacement
-- enum type is used.
-- ============================================================================

CREATE TYPE role_type_v2 AS ENUM (
    'ADMIN',
    'USER',
    'CONTROLLER'
);

-- Remove any default before changing the column type.
ALTER TABLE roles
    ALTER COLUMN role_name DROP DEFAULT;

-- Convert existing role values to the new enum.
ALTER TABLE roles
    ALTER COLUMN role_name TYPE role_type_v2
    USING role_name::text::role_type_v2;

-- Align the database column name with the Java property: roleType.
ALTER TABLE roles
    RENAME COLUMN role_name TO role_type;

-- Align index naming with the new column name.
ALTER INDEX IF EXISTS idx_roles_role_name
    RENAME TO idx_roles_role_type;

-- Align the unique constraint name with the new column name.
ALTER TABLE roles
    RENAME CONSTRAINT roles_role_name_key TO roles_role_type_key;

-- Replace the old PostgreSQL enum type.
DROP TYPE role_type;

ALTER TYPE role_type_v2
    RENAME TO role_type;


-- ============================================================================
-- SECTION 3: ALIGN PERMISSION TYPE ENUM
-- ============================================================================
--
-- Final Java PermissionType enum contains domain-specific permissions rather
-- than generic CREATE/READ/UPDATE/DELETE values.
-- ============================================================================

CREATE TYPE permission_type_v2 AS ENUM (
    'USER_CREATE',
    'USER_READ',
    'USER_UPDATE',
    'USER_DELETE',

    'USER_PROFILE_READ',
    'USER_PROFILE_UPDATE',

    'ROLE_CREATE',
    'ROLE_READ',
    'ROLE_UPDATE',
    'ROLE_DELETE',
    'ROLE_ASSIGN',
    'ROLE_REVOKE',

    'PERMISSION_CREATE',
    'PERMISSION_READ',
    'PERMISSION_UPDATE',
    'PERMISSION_DELETE',
    'PERMISSION_ASSIGN',
    'PERMISSION_REVOKE',

    'DOCUMENT_UPLOAD',
    'DOCUMENT_READ',
    'DOCUMENT_UPDATE',
    'DOCUMENT_DELETE',
    'DOCUMENT_DOWNLOAD',

    'KNOWLEDGE_CREATE',
    'KNOWLEDGE_READ',
    'KNOWLEDGE_UPDATE',
    'KNOWLEDGE_DELETE',

    'EMBEDDING_GENERATE',
    'EMBEDDING_DELETE',

    'IMAGE_UPLOAD',
    'IMAGE_PROCESS',

    'AUDIO_UPLOAD',
    'AUDIO_PROCESS',

    'VIDEO_UPLOAD',
    'VIDEO_PROCESS',

    'RAG_QUERY',

    'AGENT_EXECUTE',

    'ANALYTICS_READ',

    'NOTIFICATION_SEND',

    'SYSTEM_SETTINGS',
    'SYSTEM_MONITOR',
    'SYSTEM_BACKUP'
);

-- Remove any default before changing the column type.
ALTER TABLE permissions
    ALTER COLUMN permission_name DROP DEFAULT;

-- Convert existing permission values to the final enum.
--
-- NOTE:
-- If permission rows already exist with old V1 values such as CREATE,
-- READ, UPDATE, DELETE, APPROVE, REJECT, EXPORT, or IMPORT, this conversion
-- will fail. That is intentional: there is no safe one-to-one mapping from
-- those generic values to the final OMRAGUL permission model.
ALTER TABLE permissions
    ALTER COLUMN permission_name TYPE permission_type_v2
    USING permission_name::text::permission_type_v2;

-- Align the database column name with the Java property: permissionType.
ALTER TABLE permissions
    RENAME COLUMN permission_name TO permission_type;

-- Align index naming with the new column name.
ALTER INDEX IF EXISTS idx_permissions_permission_name
    RENAME TO idx_permissions_permission_type;

-- Align the unique constraint name with the new column name.
ALTER TABLE permissions
    RENAME CONSTRAINT permissions_permission_name_key
    TO permissions_permission_type_key;

-- Replace the old PostgreSQL enum type.
DROP TYPE permission_type;

ALTER TYPE permission_type_v2
    RENAME TO permission_type;


-- ============================================================================
-- SECTION 4: USER STATUS ENUM
-- ============================================================================
--
-- V1 already matches the final Java design:
--
-- ACTIVE
-- INACTIVE
-- SUSPENDED
-- PENDING_VERIFICATION
--
-- LOCKED is intentionally NOT part of UserStatus.
--
-- Account locking is represented separately by:
--     users.account_locked
--     users.failed_login_attempts
--
-- Soft deletion is represented separately by:
--     users.is_deleted
--     users.deleted_at
--     users.deleted_by
--
-- Therefore no ALTER TYPE is required for user_status.
-- ============================================================================


-- ============================================================================
-- SECTION 5: GENDER ENUM
-- ============================================================================
--
-- V1 already matches the final Java Gender enum:
--
-- MALE
-- FEMALE
-- OTHER
-- PREFER_NOT_TO_SAY
--
-- No change required.
-- ============================================================================


-- ============================================================================
-- SECTION 6: OTP PURPOSE ENUM
-- ============================================================================
--
-- V1 already matches the final Java OtpPurpose enum:
--
-- EMAIL_VERIFICATION
-- PHONE_VERIFICATION
-- PASSWORD_RESET
-- TWO_FACTOR_AUTH
-- ACCOUNT_UNLOCK
--
-- LOGIN_VERIFICATION is intentionally not used.
--
-- No change required.
-- ============================================================================


-- ============================================================================
-- SECTION 7: USERS TABLE ALIGNMENT
-- ============================================================================
--
-- User.java defines:
--     username -> length 100
--     email    -> length 255
--
-- V1 already has email VARCHAR(255), but username was VARCHAR(255).
-- ============================================================================

ALTER TABLE users
    ALTER COLUMN username TYPE VARCHAR(100);


-- ============================================================================
-- SECTION 8: LOGIN HISTORY TABLE ALIGNMENT
-- ============================================================================
--
-- LoginHistory.java defines username as VARCHAR(100).
-- V1 used VARCHAR(255).
-- ============================================================================

ALTER TABLE login_history
    ALTER COLUMN username TYPE VARCHAR(100);


-- ============================================================================
-- SECTION 9: UPDATE INDEX DEFINITIONS AFTER COLUMN RENAMES
-- ============================================================================
--
-- The existing index definitions continue to work after a column rename,
-- but the index names were aligned above for consistency.
--
-- No new indexes are required in this migration.
-- ============================================================================


-- ============================================================================
-- SECTION 10: COMMENTS
-- ============================================================================

COMMENT ON COLUMN roles.role_type IS
    'Role classification used by the identity service RBAC system';

COMMENT ON COLUMN permissions.permission_type IS
    'Domain-specific permission used by the identity service RBAC system';

COMMENT ON COLUMN users.account_locked IS
    'Temporary account lock state, separate from the overall UserStatus';

COMMENT ON COLUMN users.is_deleted IS
    'Soft-delete flag; deleted users are retained for audit purposes';


-- ============================================================================
-- FINAL SCHEMA EXPECTATIONS
-- ============================================================================
--
-- user_status:
--   ACTIVE
--   INACTIVE
--   SUSPENDED
--   PENDING_VERIFICATION
--
-- gender:
--   MALE
--   FEMALE
--   OTHER
--   PREFER_NOT_TO_SAY
--
-- role_type:
--   ADMIN
--   USER
--   CONTROLLER
--
-- permission_type:
--   USER_CREATE
--   USER_READ
--   USER_UPDATE
--   USER_DELETE
--   USER_PROFILE_READ
--   USER_PROFILE_UPDATE
--   ROLE_CREATE
--   ROLE_READ
--   ROLE_UPDATE
--   ROLE_DELETE
--   ROLE_ASSIGN
--   ROLE_REVOKE
--   PERMISSION_CREATE
--   PERMISSION_READ
--   PERMISSION_UPDATE
--   PERMISSION_DELETE
--   PERMISSION_ASSIGN
--   PERMISSION_REVOKE
--   DOCUMENT_UPLOAD
--   DOCUMENT_READ
--   DOCUMENT_UPDATE
--   DOCUMENT_DELETE
--   DOCUMENT_DOWNLOAD
--   KNOWLEDGE_CREATE
--   KNOWLEDGE_READ
--   KNOWLEDGE_UPDATE
--   KNOWLEDGE_DELETE
--   EMBEDDING_GENERATE
--   EMBEDDING_DELETE
--   IMAGE_UPLOAD
--   IMAGE_PROCESS
--   AUDIO_UPLOAD
--   AUDIO_PROCESS
--   VIDEO_UPLOAD
--   VIDEO_PROCESS
--   RAG_QUERY
--   AGENT_EXECUTE
--   ANALYTICS_READ
--   NOTIFICATION_SEND
--   SYSTEM_SETTINGS
--   SYSTEM_MONITOR
--   SYSTEM_BACKUP
--
-- otp_purpose:
--   EMAIL_VERIFICATION
--   PHONE_VERIFICATION
--   PASSWORD_RESET
--   TWO_FACTOR_AUTH
--   ACCOUNT_UNLOCK
--
-- login_status:
--   SUCCESS
--   FAILED
--   LOCKED
--   EXPIRED
--   LOGOUT
--
-- ============================================================================

-- END OF V2__align_identity_schema.sql
