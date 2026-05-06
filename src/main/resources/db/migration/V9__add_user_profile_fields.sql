-- V9__add_user_profile_fields.sql
-- Add profile image URL and notification preferences to users table

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS sms_notifications BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN users.profile_image_url IS 'URL to user profile picture';
COMMENT ON COLUMN users.email_notifications IS 'Whether to send email notifications';
COMMENT ON COLUMN users.sms_notifications IS 'Whether to send SMS notifications';
