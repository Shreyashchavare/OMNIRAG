-- ============================================================================
-- OMNIRAG Identity Service - Database Schema Migration
-- Database: PostgreSQL
-- Migration Version: V1
-- Purpose: Create initial identity service schema with all entities and relationships
-- ============================================================================

-- ============================================================================
-- SECTION 1: ENUM TYPES
-- ============================================================================

-- User Status Enum
CREATE TYPE user_status AS ENUM (
    'ACTIVE',
    'INACTIVE',
    'SUSPENDED',
    'PENDING_VERIFICATION'
);

-- Gender Enum
CREATE TYPE gender AS ENUM (
    'MALE',
    'FEMALE',
    'OTHER',
    'PREFER_NOT_TO_SAY'
);

-- Role Type Enum
CREATE TYPE role_type AS ENUM (
    'ADMIN',
    'USER',
    'MANAGER',
    'VIEWER',
    'EDITOR'
);

-- Permission Type Enum
CREATE TYPE permission_type AS ENUM (
    'CREATE',
    'READ',
    'UPDATE',
    'DELETE',
    'APPROVE',
    'REJECT',
    'EXPORT',
    'IMPORT'
);

-- OTP Purpose Enum
CREATE TYPE otp_purpose AS ENUM (
    'EMAIL_VERIFICATION',
    'PHONE_VERIFICATION',
    'PASSWORD_RESET',
    'TWO_FACTOR_AUTH',
    'ACCOUNT_UNLOCK'
);

-- Login Status Enum
CREATE TYPE login_status AS ENUM (
    'SUCCESS',
    'FAILED',
    'LOCKED',
    'EXPIRED'
);

-- ============================================================================
-- SECTION 2: BASE TABLES
-- ============================================================================

-- ============================================================================
-- TABLE: users
-- Description: Core user entity with authentication and status information
-- ============================================================================
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- User Credentials
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    -- User Status
    status user_status NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP WITH TIME ZONE,

    -- Audit Fields (BaseEntity)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Audit Fields (AuditableEntity)
    created_by UUID,
    updated_by UUID,
    deleted_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT users_email_not_empty CHECK (email != ''),
    CONSTRAINT users_username_not_empty CHECK (username != '')
);

-- Indexes for users table
CREATE INDEX idx_users_username ON users(username) WHERE is_deleted = FALSE;
CREATE INDEX idx_users_email ON users(email) WHERE is_deleted = FALSE;
CREATE INDEX idx_users_status ON users(status) WHERE is_deleted = FALSE;
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_is_deleted ON users(is_deleted);

-- ============================================================================
-- TABLE: user_profiles
-- Description: Extended user profile information (OneToOne with users)
-- ============================================================================
CREATE TABLE user_profiles (
    profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,

    -- Personal Information
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_number VARCHAR(20),
    gender gender,
    date_of_birth DATE,

    -- Organization & Preferences
    department_id UUID,
    timezone VARCHAR(50),
    language VARCHAR(10),
    bio TEXT,
    profile_image_url VARCHAR(500),

    -- Audit Fields (BaseEntity)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for user_profiles table
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_department_id ON user_profiles(department_id);

-- ============================================================================
-- TABLE: roles
-- Description: Role entity for RBAC (Role-Based Access Control)
-- ============================================================================
CREATE TABLE roles (
    role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name role_type NOT NULL UNIQUE,
    description TEXT,

    -- Audit Fields (BaseEntity)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Audit Fields (AuditableEntity)
    created_by UUID,
    updated_by UUID,
    deleted_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

-- Indexes for roles table
CREATE INDEX idx_roles_role_name ON roles(role_name) WHERE is_deleted = FALSE;
CREATE INDEX idx_roles_is_deleted ON roles(is_deleted);

-- ============================================================================
-- TABLE: permissions
-- Description: Permission entity for granular access control
-- ============================================================================
CREATE TABLE permissions (
    permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_name permission_type NOT NULL UNIQUE,
    description TEXT,

    -- Audit Fields (BaseEntity)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Audit Fields (AuditableEntity)
    created_by UUID,
    updated_by UUID,
    deleted_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

-- Indexes for permissions table
CREATE INDEX idx_permissions_permission_name ON permissions(permission_name) WHERE is_deleted = FALSE;
CREATE INDEX idx_permissions_is_deleted ON permissions(is_deleted);

-- ============================================================================
-- SECTION 3: ASSOCIATION/JUNCTION TABLES
-- ============================================================================

-- ============================================================================
-- TABLE: user_roles
-- Description: Association table between users and roles (ManyToMany)
-- ============================================================================
CREATE TABLE user_roles (
    user_role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,

    -- Audit Information
    assigned_by UUID,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraint: Each user can have each role only once
    CONSTRAINT user_roles_unique UNIQUE(user_id, role_id)
);

-- Indexes for user_roles table
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- ============================================================================
-- TABLE: role_permissions
-- Description: Association table between roles and permissions (ManyToMany)
-- ============================================================================
CREATE TABLE role_permissions (
    role_permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,

    -- Audit Information
    granted_by UUID,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraint: Each role can have each permission only once
    CONSTRAINT role_permissions_unique UNIQUE(role_id, permission_id)
);

-- Indexes for role_permissions table
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- ============================================================================
-- TABLE: user_permissions
-- Description: Direct permission assignment to users (with optional expiration)
-- ============================================================================
CREATE TABLE user_permissions (
    user_permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,

    -- Audit Information
    granted_by UUID,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,

    -- Constraint: Each user can have each permission only once
    CONSTRAINT user_permissions_unique UNIQUE(user_id, permission_id)
);

-- Indexes for user_permissions table
CREATE INDEX idx_user_permissions_user_id ON user_permissions(user_id);
CREATE INDEX idx_user_permissions_permission_id ON user_permissions(permission_id);
CREATE INDEX idx_user_permissions_expires_at ON user_permissions(expires_at);

-- ============================================================================
-- SECTION 4: AUTHENTICATION TABLES
-- ============================================================================

-- ============================================================================
-- TABLE: refresh_tokens
-- Description: Refresh token storage for JWT-based authentication
-- ============================================================================
CREATE TABLE refresh_tokens (
    refresh_token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,

    -- Token Information
    token VARCHAR(1000) NOT NULL UNIQUE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,

    -- Device Information
    device_name VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT
);

-- Indexes for refresh_tokens table
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_revoked ON refresh_tokens(revoked);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- ============================================================================
-- TABLE: otps
-- Description: One-Time Password (OTP) storage for various verification needs
-- ============================================================================
CREATE TABLE otps (
    otp_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,

    -- OTP Information
    otp_code VARCHAR(50) NOT NULL,
    purpose otp_purpose NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT otp_code_not_empty CHECK (otp_code != '')
);

-- Indexes for otps table
CREATE INDEX idx_otps_user_id ON otps(user_id);
CREATE INDEX idx_otps_otp_code ON otps(otp_code);
CREATE INDEX idx_otps_purpose ON otps(purpose);
CREATE INDEX idx_otps_expires_at ON otps(expires_at);
CREATE INDEX idx_otps_verified ON otps(verified);

-- ============================================================================
-- TABLE: password_history
-- Description: Track password change history for security auditing
-- ============================================================================
CREATE TABLE password_history (
    password_history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,

    -- Password Information
    password_hash VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for password_history table
CREATE INDEX idx_password_history_user_id ON password_history(user_id);
CREATE INDEX idx_password_history_changed_at ON password_history(changed_at);

-- ============================================================================
-- SECTION 5: AUDIT/SESSION TABLES
-- ============================================================================

-- ============================================================================
-- TABLE: user_sessions
-- Description: Track active user sessions for security and analytics
-- ============================================================================
CREATE TABLE user_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,

    -- Session Information
    jwt_id VARCHAR(500),
    login_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_time TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Device Information
    ip_address VARCHAR(45),
    device_name VARCHAR(255),
    user_agent TEXT
);

-- Indexes for user_sessions table
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_jwt_id ON user_sessions(jwt_id);
CREATE INDEX idx_user_sessions_active ON user_sessions(active);
CREATE INDEX idx_user_sessions_login_time ON user_sessions(login_time);

-- ============================================================================
-- TABLE: login_history
-- Description: Audit trail for all login attempts (success and failure)
-- ============================================================================
CREATE TABLE login_history (
    login_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(user_id) ON DELETE SET NULL,

    -- Login Information
    username VARCHAR(255) NOT NULL,
    login_status login_status NOT NULL,
    login_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failure_reason VARCHAR(500),

    -- Device Information
    ip_address VARCHAR(45),
    user_agent TEXT
);

-- Indexes for login_history table
CREATE INDEX idx_login_history_user_id ON login_history(user_id);
CREATE INDEX idx_login_history_username ON login_history(username);
CREATE INDEX idx_login_history_login_status ON login_history(login_status);
CREATE INDEX idx_login_history_login_time ON login_history(login_time);

-- ============================================================================
-- SECTION 6: DATABASE FUNCTIONS & TRIGGERS
-- ============================================================================

-- ============================================================================
-- FUNCTION: update_updated_at_column
-- Purpose: Automatically update the 'updated_at' timestamp on row modification
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGER: users_update_updated_at
-- ============================================================================
CREATE TRIGGER users_update_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- TRIGGER: user_profiles_update_updated_at
-- ============================================================================
CREATE TRIGGER user_profiles_update_updated_at
BEFORE UPDATE ON user_profiles
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- TRIGGER: roles_update_updated_at
-- ============================================================================
CREATE TRIGGER roles_update_updated_at
BEFORE UPDATE ON roles
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- TRIGGER: permissions_update_updated_at
-- ============================================================================
CREATE TRIGGER permissions_update_updated_at
BEFORE UPDATE ON permissions
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- SECTION 7: COMMENTS & DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE users IS 'Core user entity with authentication and account status management';
COMMENT ON TABLE user_profiles IS 'Extended user profile with personal and organizational information';
COMMENT ON TABLE roles IS 'RBAC roles for hierarchical permission management';
COMMENT ON TABLE permissions IS 'Granular permissions for fine-grained access control';
COMMENT ON TABLE user_roles IS 'Junction table linking users to roles';
COMMENT ON TABLE role_permissions IS 'Junction table linking roles to permissions';
COMMENT ON TABLE user_permissions IS 'Direct permission assignment to users with optional expiration';
COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens for session management';
COMMENT ON TABLE otps IS 'One-time passwords for email verification, password reset, and 2FA';
COMMENT ON TABLE password_history IS 'Password change history for security auditing';
COMMENT ON TABLE user_sessions IS 'Active user sessions tracking for security monitoring';
COMMENT ON TABLE login_history IS 'Complete audit trail of login attempts (success and failure)';

-- ============================================================================
-- END OF MIGRATION V1__create_identity_schema.sql
-- ============================================================================