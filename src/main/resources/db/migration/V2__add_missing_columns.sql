-- V2__add_missing_columns.sql

ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Add any other missing columns from User.java if needed

-- Verify
COMMENT ON COLUMN users.must_change_password IS 'Flag to force password change on first login';
