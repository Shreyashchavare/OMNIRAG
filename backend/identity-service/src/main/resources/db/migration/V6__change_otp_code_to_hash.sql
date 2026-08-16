ALTER TABLE otps
    RENAME COLUMN otp_code TO otp_code_hash;

ALTER TABLE otps
    ALTER COLUMN otp_code_hash TYPE VARCHAR(255);