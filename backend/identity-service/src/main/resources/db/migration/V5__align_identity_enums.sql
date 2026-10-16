-- ============================================================================
-- OMNIRAG Identity Service
-- V5: Align PostgreSQL enums with Java enums
-- ============================================================================


-- ============================================================================
-- 1. LOGIN STATUS
-- ============================================================================
-- Java:
-- SUCCESS
-- FAILED
-- LOCKED
-- EXPIRED
-- LOGOUT

ALTER TYPE login_status
    ADD VALUE IF NOT EXISTS 'LOGOUT';


-- ============================================================================
-- 2. OTP PURPOSE
-- ============================================================================
-- Java:
-- EMAIL_VERIFICATION
-- PASSWORD_RESET
-- LOGIN_VERIFICATION
--
-- Existing PostgreSQL enum may contain:
-- PHONE_VERIFICATION
-- TWO_FACTOR_AUTH
-- ACCOUNT_UNLOCK
--
-- We replace the enum safely using a temporary type.


-- Remove the default first if one exists.
ALTER TABLE otps
    ALTER COLUMN purpose DROP DEFAULT;


-- Create the new enum type.
CREATE TYPE otp_purpose_new AS ENUM
(
    'EMAIL_VERIFICATION',
    'PASSWORD_RESET',
    'LOGIN_VERIFICATION'
);


-- Convert the column to text first.
ALTER TABLE otps
    ALTER COLUMN purpose TYPE VARCHAR(50)
    USING purpose::text;


-- Convert the column to the new enum.
ALTER TABLE otps
    ALTER COLUMN purpose TYPE otp_purpose_new
    USING purpose::otp_purpose_new;


-- Remove the old enum.
DROP TYPE otp_purpose;


-- Rename the new enum to the original name.
ALTER TYPE otp_purpose_new
    RENAME TO otp_purpose;


-- ============================================================================
-- 3. USER STATUS
-- ============================================================================
-- Java:
-- ACTIVE
-- INACTIVE
-- LOCKED
-- SUSPENDED
-- DELETED
--
-- Existing PostgreSQL enum:
-- ACTIVE
-- INACTIVE
-- SUSPENDED
-- PENDING_VERIFICATION
--
-- We replace the enum safely using a temporary type.


-- IMPORTANT:
-- The old default references user_status.
-- Remove it before changing the column type.

ALTER TABLE users
    ALTER COLUMN status DROP DEFAULT;


-- Create the new enum type.
CREATE TYPE user_status_new AS ENUM
(
    'ACTIVE',
    'INACTIVE',
    'LOCKED',
    'SUSPENDED',
    'DELETED'
);


-- Convert the column to text first.
ALTER TABLE users
    ALTER COLUMN status TYPE VARCHAR(30)
    USING status::text;


-- Convert the column to the new enum.
ALTER TABLE users
    ALTER COLUMN status TYPE user_status_new
    USING status::user_status_new;


-- Remove the old enum.
DROP TYPE user_status;


-- Rename the new enum.
ALTER TYPE user_status_new
    RENAME TO user_status;


-- Restore the default using the NEW user_status type.
ALTER TABLE users
    ALTER COLUMN status SET DEFAULT 'ACTIVE'::user_status;